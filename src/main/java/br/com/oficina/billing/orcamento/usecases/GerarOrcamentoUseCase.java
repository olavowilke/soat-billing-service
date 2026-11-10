package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gera o orcamento de uma OS e responde a saga.
 * Se a OS ja tem orcamento, repete a resposta OrcamentoGerado com os dados dele (comando reenviado).
 * Item ou total invalido gera OrcamentoFalhou, nao uma excecao: a saga precisa da resposta.
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
            respostas.orcamentoGerado(cmd.sagaId(), idDaSaga(o, cmd), cmd.osId(), o.getId(), o.getValorTotal());
            return;
        }
        Orcamento novo;
        try {
            novo = Orcamento.gerar(cmd.osId(), cmd.sagaId(), cmd.correlationId(), cmd.clienteId(), montarItens(cmd), Instant.now());
        } catch (IllegalArgumentException e) {
            respostas.orcamentoFalhou(cmd.sagaId(), cmd.correlationId(), cmd.osId(), e.getMessage());
            return;
        }
        var salvo = orcamentos.salvar(novo);
        respostas.orcamentoGerado(cmd.sagaId(), salvo.getCorrelationId(), cmd.osId(), salvo.getId(),
                salvo.getValorTotal());
    }

    /** O orcamento guarda o id da saga. Se a OS ja tinha orcamento, vale o dele. */
    private static String idDaSaga(Orcamento o, GerarOrcamentoCommand cmd) {
        return o.getCorrelationId() != null ? o.getCorrelationId() : cmd.correlationId();
    }

    /** Valor invalido vira IllegalArgumentException. */
    private static List<ItemOrcamento> montarItens(GerarOrcamentoCommand cmd) {
        if (cmd.itens() == null) {
            return List.of();
        }
        return cmd.itens().stream().map(i -> new ItemOrcamento(tipo(i.tipo()), uuid(i.referenciaId()),
                i.descricao(), i.quantidade(), decimal(i.valorUnitario()))).toList();
    }

    private static TipoItem tipo(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("tipo e obrigatorio");
        }
        try {
            return TipoItem.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo invalido: " + valor);
        }
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("referenciaId invalido");
        }
    }

    private static BigDecimal decimal(String valor) {
        try {
            return new BigDecimal(valor);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("valorUnitario invalido");
        }
    }
}
