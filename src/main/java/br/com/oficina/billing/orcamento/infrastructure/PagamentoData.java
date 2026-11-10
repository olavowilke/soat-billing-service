package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagamentos")
class PagamentoData {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orcamento_id", nullable = false, unique = true)
    private OrcamentoData orcamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "mp_preference_id", length = 100)
    private String mpPreferenceId;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "link_pagamento", length = 500)
    private String linkPagamento;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "tentativas_estorno", nullable = false)
    private int tentativasEstorno;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected PagamentoData() {
    }

    PagamentoData(UUID id, OrcamentoData orcamento, StatusPagamento status, BigDecimal valor, String mpPreferenceId,
            String mpPaymentId, String linkPagamento, Instant expiraEm, int tentativasEstorno, Instant criadoEm,
            Instant atualizadoEm) {
        this.id = id;
        this.orcamento = orcamento;
        this.status = status;
        this.valor = valor;
        this.mpPreferenceId = mpPreferenceId;
        this.mpPaymentId = mpPaymentId;
        this.linkPagamento = linkPagamento;
        this.expiraEm = expiraEm;
        this.tentativasEstorno = tentativasEstorno;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    UUID getId() {
        return id;
    }

    StatusPagamento getStatus() {
        return status;
    }

    BigDecimal getValor() {
        return valor;
    }

    String getMpPreferenceId() {
        return mpPreferenceId;
    }

    String getMpPaymentId() {
        return mpPaymentId;
    }

    String getLinkPagamento() {
        return linkPagamento;
    }

    Instant getExpiraEm() {
        return expiraEm;
    }

    int getTentativasEstorno() {
        return tentativasEstorno;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }

    Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
