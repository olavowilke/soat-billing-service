package br.com.oficina.billing.orcamento.gateways;

import java.util.UUID;

/**
 * Auditoria dos estornos feitos fora da saga: pagamento aprovado no provedor quando o nosso pagamento
 * ja nao esta pendente. Grava na hora, mesmo se a transacao do chamador desfizer.
 */
public interface EstornoAvulsoGateway {

    void registrar(UUID pagamentoId, String mpPaymentId, String motivo, boolean sucesso, String erro);
}
