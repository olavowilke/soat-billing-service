package br.com.oficina.billing.orcamento.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemOrcamentoTest {

    private static final UUID REF = UUID.randomUUID();

    @Test
    void subtotalMultiplicaQuantidadePorValor() {
        var item = new ItemOrcamento(TipoItem.PECA, REF, "Filtro", 3, new BigDecimal("10.5"));
        assertThat(item.subtotal()).isEqualByComparingTo("31.50");
        assertThat(item.subtotal().scale()).isEqualTo(2);
    }

    @Test
    void quantidadeNaoPositivaFalha() {
        var valor = BigDecimal.TEN;
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, "x", 0, valor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, "x", -1, valor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valorNegativoOuNuloFalha() {
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, "x", 1, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, "x", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tipoReferenciaOuDescricaoInvalidosFalham() {
        var valor = BigDecimal.TEN;
        assertThatThrownBy(() -> new ItemOrcamento(null, REF, "x", 1, valor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, null, "x", 1, valor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, " ", 1, valor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemOrcamento(TipoItem.PECA, REF, null, 1, valor))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
