package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;

import java.util.Optional;
import java.util.UUID;

/** Busca um pagamento pelo id (usado pela pagina do pagamento fake). */
public class BuscarPagamentoUseCase {

    private final OrcamentoGateway orcamentos;

    public BuscarPagamentoUseCase(OrcamentoGateway orcamentos) {
        this.orcamentos = orcamentos;
    }

    public Optional<Pagamento> execute(UUID pagamentoId) {
        return orcamentos.buscarPorPagamentoId(pagamentoId).flatMap(Orcamento::getPagamento);
    }
}
