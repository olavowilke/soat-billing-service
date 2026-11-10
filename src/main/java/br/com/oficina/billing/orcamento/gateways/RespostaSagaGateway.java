package br.com.oficina.billing.orcamento.gateways;

import java.math.BigDecimal;
import java.util.UUID;

/** Respostas do Billing para a saga do OS Service. Cada metodo e um tipo de resposta do contrato. */
public interface RespostaSagaGateway {

    void orcamentoGerado(UUID sagaId, UUID osId, UUID orcamentoId, BigDecimal valorTotal);

    void orcamentoFalhou(UUID sagaId, UUID osId, String motivo);

    void orcamentoAprovado(UUID sagaId, UUID osId, UUID orcamentoId, UUID pagamentoId, String linkPagamento);

    void orcamentoRecusado(UUID sagaId, UUID osId, UUID orcamentoId);

    void pagamentoConfirmado(UUID sagaId, UUID osId, UUID orcamentoId, UUID pagamentoId);

    void pagamentoFalhou(UUID sagaId, UUID osId, UUID orcamentoId, UUID pagamentoId, String motivo);

    void pagamentoEstornado(UUID sagaId, UUID osId, UUID pagamentoId);

    void estornoFalhou(UUID sagaId, UUID osId, UUID pagamentoId, String motivo);
}
