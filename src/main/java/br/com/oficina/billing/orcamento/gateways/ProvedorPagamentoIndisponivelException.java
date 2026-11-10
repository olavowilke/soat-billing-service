package br.com.oficina.billing.orcamento.gateways;

/** O provedor de pagamento recusou ou nao respondeu ao criar a cobranca. A API responde 502. */
public class ProvedorPagamentoIndisponivelException extends RuntimeException {

    public ProvedorPagamentoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProvedorPagamentoIndisponivelException(String message) {
        super(message);
    }
}
