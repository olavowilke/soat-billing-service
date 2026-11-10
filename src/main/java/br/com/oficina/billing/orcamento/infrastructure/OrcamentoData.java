package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orcamentos")
class OrcamentoData {

    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false, unique = true)
    private UUID osId;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrcamento status;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    /** Nulo no objeto novo: o Spring Data entende que e insercao. */
    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "orcamento_id", nullable = false)
    private List<ItemOrcamentoData> itens = new ArrayList<>();

    @OneToOne(mappedBy = "orcamento", cascade = CascadeType.ALL, orphanRemoval = true)
    private PagamentoData pagamento;

    protected OrcamentoData() {
    }

    OrcamentoData(UUID id, UUID osId, UUID sagaId, UUID clienteId, StatusOrcamento status, BigDecimal valorTotal,
            Long versao, Instant criadoEm, Instant atualizadoEm) {
        this.id = id;
        this.osId = osId;
        this.sagaId = sagaId;
        this.clienteId = clienteId;
        this.status = status;
        this.valorTotal = valorTotal;
        this.versao = versao;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    UUID getId() {
        return id;
    }

    UUID getOsId() {
        return osId;
    }

    UUID getSagaId() {
        return sagaId;
    }

    UUID getClienteId() {
        return clienteId;
    }

    StatusOrcamento getStatus() {
        return status;
    }

    BigDecimal getValorTotal() {
        return valorTotal;
    }

    Long getVersao() {
        return versao;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    List<ItemOrcamentoData> getItens() {
        return itens;
    }

    PagamentoData getPagamento() {
        return pagamento;
    }

    void setPagamento(PagamentoData pagamento) {
        this.pagamento = pagamento;
    }
}
