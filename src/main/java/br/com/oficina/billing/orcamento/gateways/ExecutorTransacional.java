package br.com.oficina.billing.orcamento.gateways;

/**
 * Porta para rodar uma acao em uma transacao propria. Os casos de uso nao conhecem o Spring,
 * entao quem precisa de uma transacao por item de uma lista usa esta porta.
 */
public interface ExecutorTransacional {

    /**
     * Roda a acao em uma transacao nova e confirma.
     *
     * @return false se outra transacao gravou os mesmos dados antes (lock otimista). A transacao foi
     *         desfeita e nada ficou gravado. Qualquer outra falha sobe como excecao.
     */
    boolean executar(Runnable acao);
}
