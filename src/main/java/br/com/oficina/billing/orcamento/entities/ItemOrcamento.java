package br.com.oficina.billing.orcamento.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ItemOrcamento(
        TipoItem tipo, UUID referenciaId, String descricao, int quantidade, BigDecimal valorUnitario) {

    public ItemOrcamento {
        if (tipo == null) {
            throw new IllegalArgumentException("tipo e obrigatorio");
        }
        if (referenciaId == null) {
            throw new IllegalArgumentException("referenciaId e obrigatorio");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("descricao e obrigatoria");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        if (valorUnitario == null || valorUnitario.signum() < 0) {
            throw new IllegalArgumentException("valorUnitario deve ser maior ou igual a zero");
        }
    }

    public BigDecimal subtotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }
}
