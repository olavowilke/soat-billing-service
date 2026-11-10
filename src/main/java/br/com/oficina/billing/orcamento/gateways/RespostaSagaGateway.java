package br.com.oficina.billing.orcamento.gateways;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respostas do Billing para a saga do OS Service. Cada metodo e um tipo de resposta do contrato.
 * Toda resposta leva o correlationId da saga (o do comando GerarOrcamento, guardado no orcamento),
 * nunca o da requisicao HTTP que a causou.
 */
public interface RespostaSagaGateway {

    void orcamentoGerado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, BigDecimal valorTotal);

    void orcamentoFalhou(UUID sagaId, String correlationId, UUID osId, String motivo);

    void orcamentoAprovado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, UUID pagamentoId, String linkPagamento);

    void orcamentoRecusado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId);

    void pagamentoConfirmado(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, UUID pagamentoId);

    void pagamentoFalhou(UUID sagaId, String correlationId, UUID osId, UUID orcamentoId, UUID pagamentoId, String motivo);

    void pagamentoEstornado(UUID sagaId, String correlationId, UUID osId, UUID pagamentoId);

    void estornoFalhou(UUID sagaId, String correlationId, UUID osId, UUID pagamentoId, String motivo);
}
