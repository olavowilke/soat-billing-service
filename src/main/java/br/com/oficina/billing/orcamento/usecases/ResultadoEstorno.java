package br.com.oficina.billing.orcamento.usecases;

/** ESTORNADO e DESISTIU encerram o comando. TENTAR_DE_NOVO pede nova entrega da mensagem. */
public enum ResultadoEstorno {
    ESTORNADO, TENTAR_DE_NOVO, DESISTIU
}
