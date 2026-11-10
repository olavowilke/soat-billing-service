package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;

import java.time.Instant;

/**
 * Gera o orcamento de uma OS e responde a saga.
 * Se a OS ja tem orcamento, repete a resposta OrcamentoGerado com os dados dele (comando reenviado).
 * Itens invalidos geram OrcamentoFalhou, nao uma excecao: a saga precisa da resposta.
 */
public class GerarOrcamentoUseCase {

    private final OrcamentoGateway orcamentos;
    private final RespostaSagaGateway respostas;

    public GerarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas) {
        this.orcamentos = orcamentos;
        this.respostas = respostas;
    }

    public void execute(GerarOrcamentoCommand cmd) {
        var existente = orcamentos.buscarPorOsId(cmd.osId());
        if (existente.isPresent()) {
            var o = existente.get();
            respostas.orcamentoGerado(cmd.sagaId(), cmd.osId(), o.getId(), o.getValorTotal());
            return;
        }
        Orcamento novo;
        try {
            novo = Orcamento.gerar(cmd.osId(), cmd.sagaId(), cmd.clienteId(), cmd.itens(), Instant.now());
        } catch (IllegalArgumentException e) {
            respostas.orcamentoFalhou(cmd.sagaId(), cmd.osId(), e.getMessage());
            return;
        }
        var salvo = orcamentos.salvar(novo);
        respostas.orcamentoGerado(cmd.sagaId(), cmd.osId(), salvo.getId(), salvo.getValorTotal());
    }
}
