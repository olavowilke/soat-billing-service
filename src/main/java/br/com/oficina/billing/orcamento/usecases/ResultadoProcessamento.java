package br.com.oficina.billing.orcamento.usecases;

/** O que o ProcessarResultadoPagamentoUseCase fez com o resultado do provedor. */
public enum ResultadoProcessamento {
    /** Mudou o estado e respondeu a saga. */
    APLICADO,
    /** Nada a fazer: situacao pendente ou resultado repetido. */
    IGNORADO,
    /** Pagamento aprovado chegou depois da expiracao: estornado, sem resposta a saga. */
    ESTORNADO,
    /** Nenhum pagamento com esse id. */
    DESCONHECIDO
}
