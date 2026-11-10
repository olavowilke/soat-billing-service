package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.shared.BillingIT;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Testes do listener de comandos com Postgres e LocalStack. O intervalo do relay e de 1 hora no perfil de teste:
 * o teste de ponta a ponta chama {@code enviarPendentes()} na mesma thread, como o OutboxRelayIT.
 * Os ERROR esperados vao para um appender do teste e saem do console.
 */
class ComandosListenerIT extends BillingIT {

    @Autowired
    private SqsAsyncClient sqs;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OutboxRelay relay;

    private ListAppender<ILoggingEvent> logs;
    private List<Logger> loggers;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from outbox");
        loggers = List.of((Logger) LoggerFactory.getLogger(ComandosListener.class),
                (Logger) LoggerFactory.getLogger(
                        "io.awspring.cloud.sqs.listener.sink.AbstractMessageProcessingPipelineSink"));
        logs = new ListAppender<>();
        logs.start();
        loggers.forEach(l -> {
            l.addAppender(logs);
            l.setAdditive(false);
        });
    }

    @AfterEach
    void soltar() {
        // Mensagem rejeitada volta 3 vezes antes da DLQ. Espera isso acabar para os ERROR ficarem no appender.
        aguardarFilaVazia();
        loggers.forEach(l -> {
            l.detachAppender(logs);
            l.setAdditive(true);
        });
        jdbc.update("delete from outbox");
    }

    @Test
    void mesmaMensagemDuasVezesGeraUmaLinhaNoOutbox() throws Exception {
        var saga = UUID.randomUUID();
        var os = UUID.randomUUID();
        var corpo = comando(UUID.randomUUID(), saga, os, "GerarOrcamento", "corr-" + saga, itensValidos());

        enviar(corpo, "GerarOrcamento");
        enviar(corpo, "GerarOrcamento");

        aguardarFilaVazia();
        assertThat(linhasOutbox(saga)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from orcamentos where os_id = ?", Integer.class, os))
                .isEqualTo(1);
    }

    @Test
    void comandoGeraRespostaNaFila() throws Exception {
        var saga = UUID.randomUUID();
        var os = UUID.randomUUID();

        enviar(comando(UUID.randomUUID(), saga, os, "GerarOrcamento", "corr-" + saga, itensValidos()),
                "GerarOrcamento");

        await().atMost(Duration.ofSeconds(15)).until(() -> linhasOutbox(saga) == 1);
        relay.enviarPendentes();

        Message resposta = aguardarResposta(os);
        assertThat(resposta.messageAttributes().get("type").stringValue()).isEqualTo("OrcamentoGerado");
        assertThat(resposta.messageAttributes().get("correlationId").stringValue()).isEqualTo("corr-" + saga);
        JsonNode envelope = mapper.readTree(resposta.body());
        assertThat(envelope.get("sagaId").asText()).isEqualTo(saga.toString());
        assertThat(envelope.get("payload").get("valorTotal").asText()).isEqualTo("350.00");
        var orcamentoId = jdbc.queryForObject("select id from orcamentos where os_id = ?", UUID.class, os);
        assertThat(envelope.get("payload").get("orcamentoId").asText()).isEqualTo(orcamentoId.toString());
    }

    @Test
    void itensVaziosGeramOrcamentoFalhou() throws Exception {
        var saga = UUID.randomUUID();
        var os = UUID.randomUUID();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("clienteId", UUID.randomUUID());
        payload.put("itens", List.of());

        enviar(mapper.writeValueAsString(envelope(UUID.randomUUID(), saga, os, "GerarOrcamento", "corr-" + saga,
                payload)), "GerarOrcamento");

        await().atMost(Duration.ofSeconds(15)).until(() -> linhasOutbox(saga) == 1);
        assertThat(jdbc.queryForObject("select tipo from outbox where saga_id = ?", String.class, saga))
                .isEqualTo("OrcamentoFalhou");
        assertThat(jdbc.queryForObject("select count(*) from orcamentos where os_id = ?", Integer.class, os))
                .isZero();
    }

    @Test
    void comandoSemCorrelationIdGeraRespostaComCorrelationIdNaoVazio() throws Exception {
        var saga = UUID.randomUUID();
        var os = UUID.randomUUID();

        enviar(comando(UUID.randomUUID(), saga, os, "GerarOrcamento", "", itensValidos()), "GerarOrcamento");

        await().atMost(Duration.ofSeconds(15)).until(() -> linhasOutbox(saga) == 1);
        relay.enviarPendentes();

        Message resposta = aguardarResposta(os);
        assertThat(resposta.messageAttributes().get("correlationId").stringValue()).isNotBlank();
        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class, saga))
                .isNotBlank();
    }

    @Test
    void tipoDesconhecidoNaoConfirmaNemRegistra() throws Exception {
        var saga = UUID.randomUUID();
        var messageId = UUID.randomUUID();

        enviar(comando(messageId, saga, UUID.randomUUID(), "TipoQueNaoExiste", "corr-" + saga, itensValidos()),
                "TipoQueNaoExiste");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(new ArrayList<>(logs.list)).anyMatch(e -> e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("TipoQueNaoExiste")));
        assertThat(jdbc.queryForObject("select count(*) from mensagens_processadas where message_id = ?",
                Integer.class, messageId)).isZero();
        assertThat(linhasOutbox(saga)).isZero();
    }

    @Test
    void mensagemInvalidaLogaErroENaoDerrubaListener() throws Exception {
        var saga = UUID.randomUUID();
        var os = UUID.randomUUID();

        enviar("{not json", "GerarOrcamento");
        enviar(comando(UUID.randomUUID(), saga, os, "GerarOrcamento", "corr-" + saga, itensValidos()),
                "GerarOrcamento");

        await().atMost(Duration.ofSeconds(15)).until(() -> linhasOutbox(saga) == 1);
        assertThat(new ArrayList<>(logs.list)).anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("Mensagem invalida"));
    }

    private Map<String, Object> itensValidos() {
        var item = new LinkedHashMap<String, Object>();
        item.put("tipo", "SERVICO");
        item.put("referenciaId", UUID.randomUUID());
        item.put("descricao", "Troca de oleo");
        item.put("quantidade", 1);
        item.put("valorUnitario", "350.00");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("clienteId", UUID.randomUUID());
        payload.put("itens", List.of(item));
        return payload;
    }

    private Map<String, Object> envelope(UUID messageId, UUID saga, UUID os, String tipo, String correlationId,
            Map<String, Object> payload) {
        var m = new LinkedHashMap<String, Object>();
        m.put("messageId", messageId);
        m.put("sagaId", saga);
        m.put("osId", os);
        m.put("type", tipo);
        m.put("correlationId", correlationId);
        m.put("occurredAt", Instant.now().toString());
        m.put("payload", payload);
        return m;
    }

    private String comando(UUID messageId, UUID saga, UUID os, String tipo, String correlationId,
            Map<String, Object> payload) throws Exception {
        return mapper.writeValueAsString(envelope(messageId, saga, os, tipo, correlationId, payload));
    }

    private void enviar(String corpo, String tipo) {
        String url = sqs.getQueueUrl(b -> b.queueName(FILA_COMANDOS)).join().queueUrl();
        sqs.sendMessage(b -> b.queueUrl(url).messageBody(corpo)
                .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                        .dataType("String").stringValue(tipo).build()))).join();
    }

    private int linhasOutbox(UUID saga) {
        return jdbc.queryForObject("select count(*) from outbox where saga_id = ?", Integer.class, saga);
    }

    /** Espera a fila de comandos ficar sem mensagens visiveis nem em voo, para provar que a segunda copia foi lida. */
    private void aguardarFilaVazia() {
        String url = sqs.getQueueUrl(b -> b.queueName(FILA_COMANDOS)).join().queueUrl();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).until(() -> {
            var a = sqs.getQueueAttributes(b -> b.queueUrl(url).attributeNames(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).join().attributes();
            return "0".equals(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                    && "0".equals(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        });
    }

    /** Le a fila de respostas ate achar a mensagem do osId. Descarta as de outros testes. */
    private Message aguardarResposta(UUID os) {
        String url = sqs.getQueueUrl(b -> b.queueName(FILA_RESPOSTAS)).join().queueUrl();
        List<Message> achadas = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            sqs.receiveMessage(b -> b.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)
                    .messageAttributeNames("All")).join().messages().forEach(m -> {
                if (m.body().contains(os.toString())) {
                    achadas.add(m);
                }
                sqs.deleteMessage(b -> b.queueUrl(url).receiptHandle(m.receiptHandle())).join();
            });
            return !achadas.isEmpty();
        });
        return achadas.get(0);
    }
}
