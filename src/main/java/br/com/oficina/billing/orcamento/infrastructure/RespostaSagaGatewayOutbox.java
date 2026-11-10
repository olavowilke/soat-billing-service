package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.infrastructure.CorrelationIdFilter;
import br.com.oficina.billing.shared.infrastructure.mensageria.OutboxData;
import br.com.oficina.billing.shared.infrastructure.mensageria.OutboxJpaRepository;
import br.com.oficina.billing.shared.infrastructure.mensageria.TracingMensagens;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Grava cada resposta como uma linha da outbox, na transacao do chamador (MANDATORY).
 * O OutboxRelay envia a linha depois do commit.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class RespostaSagaGatewayOutbox implements RespostaSagaGateway {

    private final OutboxJpaRepository repository;
    private final ObjectMapper mapper;

    RespostaSagaGatewayOutbox(OutboxJpaRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void orcamentoGerado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, BigDecimal valorTotal) {
        gravar("OrcamentoGerado", sagaId, correlationId, osId, p -> {
            p.put("orcamentoId", orcamentoId.toString());
            p.put("valorTotal", valorTotal.setScale(2, RoundingMode.HALF_UP).toPlainString());
        });
    }

    @Override
    public void orcamentoFalhou(UUID sagaId, String correlationId, UUID osId, String motivo) {
        gravar("OrcamentoFalhou", sagaId, correlationId, osId, p -> p.put("motivo", motivo));
    }

    @Override
    public void orcamentoAprovado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId,
            UUID pagamentoId, String linkPagamento) {
        gravar("OrcamentoAprovado", sagaId, correlationId, osId, p -> {
            p.put("orcamentoId", orcamentoId.toString());
            p.put("pagamentoId", pagamentoId.toString());
            p.put("linkPagamento", linkPagamento);
        });
    }

    @Override
    public void orcamentoRecusado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId) {
        gravar("OrcamentoRecusado", sagaId, correlationId, osId, p -> p.put("orcamentoId", orcamentoId.toString()));
    }

    @Override
    public void pagamentoConfirmado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, UUID pagamentoId) {
        gravar("PagamentoConfirmado", sagaId, correlationId, osId, p -> {
            p.put("orcamentoId", orcamentoId.toString());
            p.put("pagamentoId", pagamentoId.toString());
        });
    }

    @Override
    public void pagamentoFalhou(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, UUID pagamentoId, String motivo) {
        gravar("PagamentoFalhou", sagaId, correlationId, osId, p -> {
            p.put("orcamentoId", orcamentoId.toString());
            p.put("pagamentoId", pagamentoId.toString());
            p.put("motivo", motivo);
        });
    }

    @Override
    public void pagamentoEstornado(UUID sagaId, String correlationId, UUID osId, UUID pagamentoId) {
        gravar("PagamentoEstornado", sagaId, correlationId, osId, p -> p.put("pagamentoId", pagamentoId.toString()));
    }

    @Override
    public void estornoFalhou(UUID sagaId, String correlationId, UUID osId, UUID pagamentoId, String motivo) {
        gravar("EstornoFalhou", sagaId, correlationId, osId, p -> {
            // Nulo quando a OS nao tem orcamento nem pagamento.
            p.put("pagamentoId", pagamentoId == null ? null : pagamentoId.toString());
            p.put("motivo", motivo);
        });
    }

    private void gravar(String tipo, UUID sagaId, String correlationId, UUID osId, Consumer<ObjectNode> montar) {
        var payload = mapper.createObjectNode();
        montar.accept(payload);
        repository.save(new OutboxData(UUID.randomUUID(), tipo, sagaId, osId, idDeCorrelacao(correlationId),
                payload.toString(), Instant.now(), traceHeaders()));
    }

    /** Captura o trace agora, na thread da transacao. O relay roda em outra thread e envia este valor. */
    private String traceHeaders() {
        var cabecalhos = TracingMensagens.cabecalhosSaida();
        if (cabecalhos.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(cabecalhos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cabecalhos de trace invalidos", e);
        }
    }

    /** Sem id da saga (dado antigo), cai no id da requisicao e por fim num id novo. */
    private static String idDeCorrelacao(String daSaga) {
        if (daSaga != null && !daSaga.isBlank()) {
            return daSaga;
        }
        var doMdc = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        return doMdc == null || doMdc.isBlank() ? UUID.randomUUID().toString() : doMdc;
    }
}
