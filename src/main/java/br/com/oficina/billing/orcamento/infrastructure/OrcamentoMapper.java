package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Converte entre o dominio e as entidades JPA. O dominio nao conhece JPA. */
@Component
class OrcamentoMapper {

    /**
     * @param novo true se o orcamento ainda nao existe no banco. A versao fica nula para o JPA inserir.
     */
    OrcamentoData paraData(Orcamento o, boolean novo) {
        var data = new OrcamentoData(o.getId(), o.getOsId(), o.getSagaId(), o.getCorrelationId(), o.getClienteId(), o.getStatus(),
                o.getValorTotal(), novo ? null : o.getVersao(), o.getCriadoEm(), o.getAtualizadoEm());
        List<ItemOrcamento> itens = o.getItens();
        for (int i = 0; i < itens.size(); i++) {
            var item = itens.get(i);
            data.getItens().add(new ItemOrcamentoData(idDoItem(o.getId(), i), item.tipo(), item.referenciaId(),
                    item.descricao(), item.quantidade(), item.valorUnitario()));
        }
        o.getPagamento().ifPresent(p -> data.setPagamento(paraData(p, data)));
        return data;
    }

    Orcamento paraDominio(OrcamentoData data) {
        var itens = data.getItens().stream()
                .map(i -> new ItemOrcamento(i.getTipo(), i.getReferenciaId(), i.getDescricao(), i.getQuantidade(),
                        i.getValorUnitario()))
                .toList();
        var pagamento = data.getPagamento() == null ? null : paraDominio(data.getPagamento());
        return Orcamento.reconstituir(data.getId(), data.getOsId(), data.getSagaId(), data.getCorrelationId(), data.getClienteId(),
                data.getStatus(), itens, data.getValorTotal(), pagamento, data.getVersao(), data.getCriadoEm(),
                data.getAtualizadoEm());
    }

    private PagamentoData paraData(Pagamento p, OrcamentoData orcamento) {
        return new PagamentoData(p.getId(), orcamento, p.getStatus(), p.getValor(), p.getMpPreferenceId(),
                p.getMpPaymentId(), p.getLinkPagamento(), p.getExpiraEm(), p.getTentativasEstorno(), p.getCriadoEm(),
                p.getAtualizadoEm());
    }

    private Pagamento paraDominio(PagamentoData d) {
        return Pagamento.reconstituir(d.getId(), d.getStatus(), d.getValor(), d.getExpiraEm(), d.getMpPreferenceId(),
                d.getMpPaymentId(), d.getLinkPagamento(), d.getTentativasEstorno(), d.getCriadoEm(),
                d.getAtualizadoEm());
    }

    /** O item nao tem id no dominio. Um id estavel evita apagar e recriar os itens a cada gravacao. */
    private static UUID idDoItem(UUID orcamentoId, int posicao) {
        return UUID.nameUUIDFromBytes((orcamentoId + ":" + posicao).getBytes(StandardCharsets.UTF_8));
    }
}
