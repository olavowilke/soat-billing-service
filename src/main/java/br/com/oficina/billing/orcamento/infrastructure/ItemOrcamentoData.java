package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.TipoItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "itens_orcamento")
class ItemOrcamentoData {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoItem tipo;

    @Column(name = "referencia_id", nullable = false)
    private UUID referenciaId;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    protected ItemOrcamentoData() {
    }

    ItemOrcamentoData(UUID id, TipoItem tipo, UUID referenciaId, String descricao, int quantidade,
            BigDecimal valorUnitario) {
        this.id = id;
        this.tipo = tipo;
        this.referenciaId = referenciaId;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
    }

    UUID getId() {
        return id;
    }

    TipoItem getTipo() {
        return tipo;
    }

    UUID getReferenciaId() {
        return referenciaId;
    }

    String getDescricao() {
        return descricao;
    }

    int getQuantidade() {
        return quantidade;
    }

    BigDecimal getValorUnitario() {
        return valorUnitario;
    }
}
