package br.com.oficina.billing.orcamento.entities;

public class TransicaoInvalidaException extends RuntimeException {

    public TransicaoInvalidaException(String entidade, Object de, String acao) {
        super("Transicao invalida em " + entidade + ": " + acao + " a partir de " + de);
    }
}
