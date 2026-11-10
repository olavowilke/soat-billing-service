package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** O cliente recusa o orcamento: grava e responde OrcamentoRecusado. */
public class RecusarOrcamentoUseCase {

    private final OrcamentoGateway orcamentos;
    private final RespostaSagaGateway respostas;
    private final Clock clock;

    public RecusarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas, Clock clock) {
        this.orcamentos = orcamentos;
        this.respostas = respostas;
        this.clock = clock;
    }

    public Orcamento execute(UUID orcamentoId, Optional<UUID> clienteId) {
        Orcamento orcamento = orcamentos.buscarPorId(orcamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento", orcamentoId));
        ClienteNaoAutorizadoException.exigirDono(orcamento, clienteId);

        orcamento.recusar(clock.instant());
        Orcamento salvo = orcamentos.salvar(orcamento);
        respostas.orcamentoRecusado(salvo.getSagaId(), salvo.getCorrelationId(), salvo.getOsId(), salvo.getId());
        return salvo;
    }
}
