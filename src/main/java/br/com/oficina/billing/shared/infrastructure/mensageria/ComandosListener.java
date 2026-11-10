package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoCommand;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.Trace;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consome os comandos da Saga. Modo de ack ON_SUCCESS: qualquer excecao deixa a mensagem na fila,
 * que a entrega de novo e, depois de 3 tentativas, manda para a DLQ.
 *
 * <p>Uma transacao (TransactionTemplate) cobre o registro em mensagens_processadas, o caso de uso e a gravacao
 * da resposta na outbox. Se algo falha, tudo desfaz, inclusive a marca de processada.
 */
@Component
public class ComandosListener {

    static final String TIPO_GERAR_ORCAMENTO = "GerarOrcamento";

    private static final Logger log = LoggerFactory.getLogger(ComandosListener.class);

    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final MensagemProcessadaJpaRepository processadas;
    private final GerarOrcamentoUseCase gerarOrcamento;

    public ComandosListener(ObjectMapper mapper, TransactionTemplate tx, MensagemProcessadaJpaRepository processadas,
            GerarOrcamentoUseCase gerarOrcamento) {
        this.mapper = mapper;
        this.tx = tx;
        this.processadas = processadas;
        this.gerarOrcamento = gerarOrcamento;
    }

    @Trace(dispatcher = true)
    @SqsListener(value = "${billing.filas.comandos}", acknowledgementMode = "ON_SUCCESS")
    public void receber(String corpo,
            @Header(name = TracingMensagens.ATRIBUTO_NEWRELIC, required = false) String newrelic) {
        TracingMensagens.aceitar(newrelic == null ? Map.of() : Map.of(TracingMensagens.ATRIBUTO_NEWRELIC, newrelic));
        MensagemSaga mensagem = ler(corpo);
        try {
            if (mensagem.correlationId() != null && !mensagem.correlationId().isBlank()) {
                MDC.put("correlationId", mensagem.correlationId());
            }
            MDC.put("sagaId", String.valueOf(mensagem.sagaId()));
            tx.executeWithoutResult(status -> processar(mensagem));
        } finally {
            MDC.remove("correlationId");
            MDC.remove("sagaId");
        }
    }

    private void processar(MensagemSaga mensagem) {
        if (!TIPO_GERAR_ORCAMENTO.equals(mensagem.type())) {
            log.error("Tipo de mensagem desconhecido {}, messageId={}", mensagem.type(), mensagem.messageId());
            throw new IllegalArgumentException("Tipo de mensagem desconhecido: " + mensagem.type());
        }
        if (processadas.registrar(mensagem.messageId(), Instant.now()) == 0) {
            log.info("Mensagem ja processada, ignorada: messageId={}", mensagem.messageId());
            return;
        }
        despachar(mensagem);
    }

    /** Um ramo por tipo de comando. A Task 5 acrescenta EstornarPagamento aqui e na checagem de tipo acima. */
    private void despachar(MensagemSaga mensagem) {
        if (TIPO_GERAR_ORCAMENTO.equals(mensagem.type())) {
            gerarOrcamento.execute(paraGerarOrcamento(mensagem));
        }
    }

    private MensagemSaga ler(String corpo) {
        try {
            MensagemSaga mensagem = mapper.readValue(corpo, MensagemSaga.class);
            if (mensagem.messageId() == null || mensagem.sagaId() == null || mensagem.osId() == null
                    || mensagem.type() == null || mensagem.payload() == null || mensagem.payload().isNull()) {
                throw new IllegalArgumentException("Envelope sem messageId, sagaId, osId, type ou payload");
            }
            return mensagem;
        } catch (Exception e) {
            log.error("Mensagem invalida, nao confirmada: {}", e.getMessage());
            throw new IllegalArgumentException("Mensagem invalida", e);
        }
    }

    private GerarOrcamentoCommand paraGerarOrcamento(MensagemSaga mensagem) {
        JsonNode payload = mensagem.payload();
        List<GerarOrcamentoCommand.ItemComando> itens = new ArrayList<>();
        for (JsonNode i : payload.path("itens")) {
            itens.add(new GerarOrcamentoCommand.ItemComando(i.path("tipo").asText(null),
                    i.path("referenciaId").asText(null), i.path("descricao").asText(null),
                    i.path("quantidade").asInt(), i.path("valorUnitario").asText(null)));
        }
        return new GerarOrcamentoCommand(mensagem.sagaId(), mensagem.osId(),
                UUID.fromString(payload.path("clienteId").asText()), itens);
    }
}
