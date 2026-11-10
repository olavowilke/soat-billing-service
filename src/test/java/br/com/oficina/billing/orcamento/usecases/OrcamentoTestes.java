package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fabricas de orcamento para os testes dos casos de uso. */
final class OrcamentoTestes {

    static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

    private OrcamentoTestes() {
    }

    static Orcamento gerado(UUID clienteId) {
        return Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), clienteId, List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1,
                        new BigDecimal("100.00"))), AGORA);
    }

    static Orcamento aprovado(UUID clienteId) {
        Orcamento o = gerado(clienteId);
        o.aprovar(AGORA, Duration.ofMinutes(30));
        return o;
    }
}
