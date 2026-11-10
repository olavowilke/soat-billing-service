package br.com.oficina.billing.orcamento.usecases;

import java.util.List;
import java.util.UUID;

/**
 * Comando da saga. Os itens chegam crus: o caso de uso os valida ao montar o dominio e responde
 * OrcamentoFalhou se algum for invalido.
 */
public record GerarOrcamentoCommand(UUID sagaId, String correlationId, UUID osId, UUID clienteId, List<ItemComando> itens) {

    /** Item como veio da mensagem. valorUnitario e texto decimal, por exemplo "350.00". */
    public record ItemComando(String tipo, String referenciaId, String descricao, int quantidade,
            String valorUnitario) {
    }
}
