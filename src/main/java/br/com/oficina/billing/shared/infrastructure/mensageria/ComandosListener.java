package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.orcamento.usecases.EstornarPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoCommand;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoEstorno;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newrelic.api.agent.Trace;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consome os comandos da Saga. Modo de ack ON_SUCCESS: qualquer excecao deixa a mensagem na fila,
 * que a entrega de novo e, depois de 3 tentativas, manda para a DLQ.
 *
 * <p>Uma transacao (TransactionTemplate) cobre o registro em mensagens_processadas, o caso de uso e a gravacao
 * da resposta na outbox. Se algo falha, tudo desfaz, inclusive a marca de processada.
 *
 * <p>EstornarPagamento e a excecao. A contagem de falhas do provedor (tentativas_estorno) precisa sobreviver
 * entre as entregas, entao o caso de uso roda em transacao propria SEM registrar a mensagem antes. A mensagem so e
 * registrada quando o resultado e ESTORNADO ou DESISTIU, na mesma transacao da resposta. Com TENTAR_DE_NOVO a
 * transacao grava a tentativa e confirma; depois o listener lanca excecao, fora da transacao, e o SQS entrega de
 * novo. Na terceira falha o caso de uso desiste e publica EstornoFalhou, entao a mensagem sai da fila.
 */
@Component
public class ComandosListener {

    static final String TIPO_GERAR_ORCAMENTO = "GerarOrcamento";
    static final String TIPO_ESTORNAR_PAGAMENTO = "EstornarPagamento";

    private static final Logger log = LoggerFactory.getLogger(ComandosListener.class);

    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final MensagemProcessadaJpaRepository processadas;
    private final GerarOrcamentoUseCase gerarOrcamento;
    private final EstornarPagamentoUseCase estornarPagamento;

    public ComandosListener(ObjectMapper mapper, TransactionTemplate tx, MensagemProcessadaJpaRepository processadas,
            GerarOrcamentoUseCase gerarOrcamento, EstornarPagamentoUseCase estornarPagamento) {
        this.mapper = mapper;
        this.tx = tx;
        this.processadas = processadas;
        this.gerarOrcamento = gerarOrcamento;
        this.estornarPagamento = estornarPagamento;
    }

    @Trace(dispatcher = true)
    @SqsListener(value = "${billing.filas.comandos}", acknowledgementMode = "ON_SUCCESS")
    public void receber(String corpo,
            @Header(name = TracingMensagens.ATRIBUTO_NEWRELIC, required = false) String newrelic,
            @Header(name = SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT,
                    required = false) String receiveCount) {
        TracingMensagens.aceitar(newrelic == null ? Map.of() : Map.of(TracingMensagens.ATRIBUTO_NEWRELIC, newrelic));
        MensagemSaga mensagem = ler(corpo);
        try {
            if (mensagem.correlationId() != null && !mensagem.correlationId().isBlank()) {
                MDC.put("correlationId", mensagem.correlationId());
            }
            MDC.put("sagaId", String.valueOf(mensagem.sagaId()));
            if (TIPO_ESTORNAR_PAGAMENTO.equals(mensagem.type())) {
                receberEstorno(mensagem, contarEntregas(receiveCount));
            } else {
                tx.executeWithoutResult(status -> processar(mensagem));
            }
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

    /** Um ramo por tipo de comando que registra a mensagem antes do caso de uso. EstornarPagamento tem fluxo proprio. */
    private void despachar(MensagemSaga mensagem) {
        if (TIPO_GERAR_ORCAMENTO.equals(mensagem.type())) {
            gerarOrcamento.execute(paraGerarOrcamento(mensagem));
        }
    }

    private void receberEstorno(MensagemSaga mensagem, int receiveCount) {
        log.info("EstornarPagamento: osId={}, motivo={}", mensagem.osId(), mensagem.payload().path("motivo").asText(""));
        Optional<ResultadoEstorno> resultado = tx.execute(status -> estornar(status, mensagem, receiveCount));
        if (resultado.orElse(null) == ResultadoEstorno.TENTAR_DE_NOVO) {
            // Fora da transacao: a tentativa ja foi gravada. A excecao deixa a mensagem na fila para nova entrega.
            throw new EstornoPendenteException(mensagem.osId());
        }
    }

    /** Vazio quando a mensagem ja foi processada: nada a fazer. */
    private Optional<ResultadoEstorno> estornar(TransactionStatus status, MensagemSaga mensagem, int receiveCount) {
        if (processadas.existsById(mensagem.messageId())) {
            log.info("Mensagem ja processada, ignorada: messageId={}", mensagem.messageId());
            return Optional.empty();
        }
        ResultadoEstorno resultado = estornarPagamento.execute(mensagem.sagaId(), mensagem.osId(), receiveCount);
        if (resultado != ResultadoEstorno.TENTAR_DE_NOVO
                && processadas.registrar(mensagem.messageId(), Instant.now()) == 0) {
            // Outra entrega da mesma mensagem registrou antes. Desfaz a resposta duplicada.
            log.info("Mensagem ja processada em paralelo, resposta descartada: messageId={}", mensagem.messageId());
            status.setRollbackOnly();
        }
        return Optional.of(resultado);
    }

    /** 0 quando o SQS nao manda o contador: o caso de uso usa as tentativas gravadas. */
    private static int contarEntregas(String receiveCount) {
        try {
            return receiveCount == null ? 0 : Integer.parseInt(receiveCount.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pede nova entrega: o provedor falhou e ainda ha tentativas. */
    static class EstornoPendenteException extends RuntimeException {
        EstornoPendenteException(UUID osId) {
            super("Estorno falhou no provedor, nova tentativa pedida: osId=" + osId);
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
