package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.shared.BillingIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

/**
 * Testes do relay. O intervalo do agendador e de 1 hora no perfil de teste, entao nenhuma rodada roda sozinha:
 * cada teste chama {@code enviarPendentes()} na mesma thread. Isso evita stub do spy concorrente com o agendador.
 * O agendador real e testado em {@link OutboxRelaySchedulerIT}.
 */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayIT extends BillingIT {

    private static final String TIPO_FALHA = "FalhaForcada";

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private SqsAsyncClient sqs;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from outbox");
    }

    private UUID inserir(UUID sagaId, String tipo, String payload, int segundosAtras) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into outbox (id, tipo, saga_id, os_id, correlation_id, payload, criado_em)
                values (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, tipo, sagaId, UUID.randomUUID(), "corr-" + id, payload,
                Timestamp.from(Instant.now().minusSeconds(segundosAtras)));
        return id;
    }

    private UUID inserirRecusado(UUID sagaId, int segundosAtras) {
        return inserir(sagaId, "OrcamentoRecusado", "{\"orcamentoId\":\"" + UUID.randomUUID() + "\"}",
                segundosAtras);
    }

    private boolean enviada(UUID id) {
        return jdbc.queryForObject("select enviado_em is not null from outbox where id = ?", Boolean.class, id);
    }

    private int tentativas(UUID id) {
        return jdbc.queryForObject("select tentativas from outbox where id = ?", Integer.class, id);
    }

    private void fimDoBackoff() {
        jdbc.update("update outbox set proxima_tentativa_em = null");
    }

    private List<Message> receber(Set<UUID> sagas, int esperadas) {
        var encontradas = new ArrayList<Message>();
        var queueUrl = sqs.getQueueUrl(b -> b.queueName(FILA_RESPOSTAS)).join().queueUrl();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            var resposta = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                    .messageAttributeNames("All").waitTimeSeconds(1).maxNumberOfMessages(10).build()).join();
            resposta.messages().stream()
                    .filter(m -> sagas.stream().anyMatch(s -> m.body().contains(s.toString())))
                    .forEach(encontradas::add);
            return encontradas.size() >= esperadas;
        });
        return encontradas;
    }

    @Test
    void relayEnviaEnvelopeEAtributosDoContrato() throws Exception {
        var saga1 = UUID.randomUUID();
        var saga2 = UUID.randomUUID();
        var primeira = inserir(saga1, "OrcamentoGerado", "{\"orcamentoId\":\"" + UUID.randomUUID()
                + "\",\"valorTotal\":\"350.00\"}", 2);
        var segunda = inserirRecusado(saga2, 1);

        relay.enviarPendentes();

        var mensagens = receber(Set.of(saga1, saga2), 2);
        assertThat(mensagens).extracting(m -> m.messageAttributes().get("type").stringValue())
                .containsExactlyInAnyOrder("OrcamentoGerado", "OrcamentoRecusado");
        var gerado = mensagens.stream()
                .filter(m -> "OrcamentoGerado".equals(m.messageAttributes().get("type").stringValue()))
                .findFirst().orElseThrow();
        assertThat(gerado.messageAttributes().get("correlationId").stringValue()).isEqualTo("corr-" + primeira);
        assertThat(gerado.messageAttributes()).doesNotContainKey("newrelic");
        var envelope = mapper.readTree(gerado.body());
        assertThat(envelope.get("messageId").asText()).isEqualTo(primeira.toString());
        assertThat(envelope.get("sagaId").asText()).isEqualTo(saga1.toString());
        assertThat(envelope.get("type").asText()).isEqualTo("OrcamentoGerado");
        assertThat(envelope.get("occurredAt").asText()).endsWith("Z");
        assertThat(envelope.get("payload").get("valorTotal").asText()).isEqualTo("350.00");
        assertThat(enviada(primeira)).isTrue();
        assertThat(enviada(segunda)).isTrue();
    }

    @Test
    void relayEnviaTraceGravadoNaLinhaEUsaIdDaLinhaSeCorrelationIdVazio() {
        var saga = UUID.randomUUID();
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into outbox (id, tipo, saga_id, os_id, correlation_id, payload, criado_em, trace_headers)
                values (?, 'OrcamentoRecusado', ?, ?, '', '{}'::jsonb, now(), '{"newrelic":"abc123"}')
                """, id, saga, UUID.randomUUID());

        relay.enviarPendentes();

        var mensagem = receber(Set.of(saga), 1).get(0);
        assertThat(mensagem.messageAttributes().get("newrelic").stringValue()).isEqualTo("abc123");
        assertThat(mensagem.messageAttributes().get("correlationId").stringValue()).isEqualTo(id.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayMantemLinhaQuandoEnvioFalha(CapturedOutput saida) {
        doThrow(new IllegalStateException("fila fora do ar")).when(sqsTemplate).send(any(Consumer.class));
        var id = inserirRecusado(UUID.randomUUID(), 0);

        relay.enviarPendentes();

        assertThat(enviada(id)).isFalse();
        assertThat(tentativas(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select proxima_tentativa_em > now() from outbox where id = ?",
                Boolean.class, id)).isTrue();
        assertThat(saida.getOut()).contains("Falha ao enviar outbox id=" + id).contains("fila fora do ar");

        // Em backoff: a rodada seguinte nao tenta de novo.
        relay.enviarPendentes();
        Mockito.verify(sqsTemplate, times(1)).send(any(Consumer.class));
        assertThat(tentativas(id)).isEqualTo(1);

        // Fim do backoff com a fila ainda fora: conta outra falha.
        fimDoBackoff();
        relay.enviarPendentes();
        assertThat(tentativas(id)).isEqualTo(2);

        // Fila volta: a linha segue e e enviada.
        reset(sqsTemplate);
        fimDoBackoff();
        relay.enviarPendentes();
        assertThat(enviada(id)).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void falhaBloqueiaSoASagaEPreservaOrdem(CapturedOutput saida) {
        // Falha so para o tipo TIPO_FALHA. O resto passa pelo template real.
        doAnswer(invocacao -> {
            Consumer<SqsSendOptions> opcoes = invocacao.getArgument(0);
            var gravador = mock(SqsSendOptions.class, RETURNS_SELF);
            opcoes.accept(gravador);
            var tipo = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(gravador).header(eq("type"), tipo.capture());
            if (TIPO_FALHA.equals(tipo.getValue())) {
                throw new IllegalStateException("falha forcada");
            }
            return invocacao.callRealMethod();
        }).when(sqsTemplate).send(any(Consumer.class));
        var sagaComFalha = UUID.randomUUID();
        var outraSaga = UUID.randomUUID();
        var falha = inserir(sagaComFalha, TIPO_FALHA, "{}", 3);
        var depoisDaFalha = inserirRecusado(sagaComFalha, 2);
        var outra = inserirRecusado(outraSaga, 1);

        relay.enviarPendentes();

        assertThat(enviada(outra)).isTrue();
        assertThat(enviada(falha)).isFalse();
        assertThat(tentativas(falha)).isEqualTo(1);
        assertThat(enviada(depoisDaFalha)).as("linha mais nova da mesma saga espera a anterior").isFalse();
        assertThat(saida.getOut()).contains("Falha ao enviar outbox id=" + falha).contains("falha forcada");

        fimDoBackoff();
        relay.enviarPendentes();
        assertThat(tentativas(falha)).isEqualTo(2);
        assertThat(enviada(depoisDaFalha)).isFalse();

        reset(sqsTemplate);
        fimDoBackoff();
        relay.enviarPendentes();
        assertThat(enviada(falha)).isTrue();
        assertThat(enviada(depoisDaFalha)).isFalse();
        relay.enviarPendentes();
        assertThat(enviada(depoisDaFalha)).isTrue();
        var ordem = jdbc.queryForList("select id from outbox where saga_id = ? order by enviado_em", UUID.class,
                sagaComFalha);
        assertThat(ordem).containsExactly(falha, depoisDaFalha);
    }
}
