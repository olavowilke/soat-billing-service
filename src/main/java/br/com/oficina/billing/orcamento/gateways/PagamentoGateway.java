package br.com.oficina.billing.orcamento.gateways;

import java.math.BigDecimal;
import java.util.UUID;

/** Porta do provedor de pagamento (Mercado Pago, so sandbox, ou o fake). */
public interface PagamentoGateway {

    CobrancaCriada criarCobranca(UUID pagamentoId, String descricao, BigDecimal valor);

    ResultadoConsultaPagamento consultar(String mpPaymentId);

    /** Lanca excecao se o provedor nao conseguir estornar. O pagamentoId serve de chave de idempotencia. */
    void estornar(String mpPaymentId, UUID pagamentoId);
}
