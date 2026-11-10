package br.com.oficina.billing.orcamento.gateways;

import java.util.UUID;

public record ResultadoConsultaPagamento(UUID pagamentoId, String mpPaymentId, SituacaoPagamentoExterno situacao) {
}
