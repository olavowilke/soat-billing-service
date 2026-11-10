package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;

import java.util.Optional;
import java.util.UUID;

/** Busca o orcamento de uma OS para o cliente dono (ou para a equipe, sem cabecalho). */
public class BuscarOrcamentoUseCase {

    private final OrcamentoGateway orcamentos;

    public BuscarOrcamentoUseCase(OrcamentoGateway orcamentos) {
        this.orcamentos = orcamentos;
    }

    public Orcamento execute(UUID osId, Optional<UUID> clienteId) {
        Orcamento orcamento = orcamentos.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento da OS", osId));
        ClienteNaoAutorizadoException.exigirDono(orcamento, clienteId);
        return orcamento;
    }
}
