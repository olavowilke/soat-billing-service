package br.com.oficina.billing.orcamento.presenters;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.presenters.OrcamentoResponse.ItemResponse;
import br.com.oficina.billing.orcamento.presenters.OrcamentoResponse.PagamentoResponse;

public final class OrcamentoPresenter {

    private OrcamentoPresenter() {
    }

    public static OrcamentoResponse toResponse(Orcamento orcamento) {
        Pagamento pagamento = orcamento.getPagamento().orElse(null);
        return new OrcamentoResponse(
                orcamento.getId(),
                orcamento.getOsId(),
                orcamento.getClienteId(),
                orcamento.getStatus().name(),
                orcamento.getValorTotal(),
                orcamento.getItens().stream()
                        .map(i -> new ItemResponse(i.tipo().name(), i.referenciaId(), i.descricao(),
                                i.quantidade(), i.valorUnitario(), i.subtotal()))
                        .toList(),
                pagamento == null ? null : new PagamentoResponse(pagamento.getId(), pagamento.getStatus().name(),
                        pagamento.getValor(), pagamento.getExpiraEm()),
                pagamento == null ? null : pagamento.getLinkPagamento());
    }
}
