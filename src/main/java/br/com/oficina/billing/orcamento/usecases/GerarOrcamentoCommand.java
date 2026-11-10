package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;

import java.util.List;
import java.util.UUID;

public record GerarOrcamentoCommand(UUID sagaId, UUID osId, UUID clienteId, List<ItemOrcamento> itens) {
}
