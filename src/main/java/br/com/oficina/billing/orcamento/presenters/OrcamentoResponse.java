package br.com.oficina.billing.orcamento.presenters;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrcamentoResponse(
        UUID id,
        UUID osId,
        UUID clienteId,
        String status,
        BigDecimal valorTotal,
        List<ItemResponse> itens,
        PagamentoResponse pagamento,
        String linkPagamento) {

    public record ItemResponse(String tipo, UUID referenciaId, String descricao, int quantidade,
            BigDecimal valorUnitario, BigDecimal subtotal) {
    }

    public record PagamentoResponse(UUID id, String status, BigDecimal valor, Instant expiraEm) {
    }
}
