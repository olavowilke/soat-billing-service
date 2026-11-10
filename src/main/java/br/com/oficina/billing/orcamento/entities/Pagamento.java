package br.com.oficina.billing.orcamento.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public class Pagamento {

    private final UUID id;
    private StatusPagamento status;
    private final BigDecimal valor;
    private final Instant expiraEm;
    private String mpPreferenceId;
    private String mpPaymentId;
    private String linkPagamento;
    private int tentativasEstorno;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    private Pagamento(UUID id, StatusPagamento status, BigDecimal valor, Instant expiraEm, String mpPreferenceId,
            String mpPaymentId, String linkPagamento, int tentativasEstorno, Instant criadoEm, Instant atualizadoEm) {
        this.id = id;
        this.status = status;
        this.valor = valor.setScale(2, RoundingMode.HALF_UP);
        this.expiraEm = expiraEm;
        this.mpPreferenceId = mpPreferenceId;
        this.mpPaymentId = mpPaymentId;
        this.linkPagamento = linkPagamento;
        this.tentativasEstorno = tentativasEstorno;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Pagamento criar(BigDecimal valor, Instant expiraEm, Instant agora) {
        return new Pagamento(UUID.randomUUID(), StatusPagamento.PENDENTE, valor, expiraEm, null, null, null, 0, agora,
                agora);
    }

    public static Pagamento reconstituir(UUID id, StatusPagamento status, BigDecimal valor, Instant expiraEm,
            String mpPreferenceId, String mpPaymentId, String linkPagamento, int tentativasEstorno, Instant criadoEm,
            Instant atualizadoEm) {
        return new Pagamento(id, status, valor, expiraEm, mpPreferenceId, mpPaymentId, linkPagamento,
                tentativasEstorno, criadoEm, atualizadoEm);
    }

    public void definirCobranca(String preferenceId, String link) {
        this.mpPreferenceId = preferenceId;
        this.linkPagamento = link;
    }

    public void registrarFalhaEstorno() {
        tentativasEstorno++;
    }

    public boolean vencido(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    void aprovar(String mpPaymentId, Instant agora) {
        transitar(StatusPagamento.PENDENTE, StatusPagamento.APROVADO, "aprovar", agora);
        this.mpPaymentId = mpPaymentId;
    }

    void rejeitar(Instant agora) {
        transitar(StatusPagamento.PENDENTE, StatusPagamento.REJEITADO, "rejeitar", agora);
    }

    void expirar(Instant agora) {
        transitar(StatusPagamento.PENDENTE, StatusPagamento.EXPIRADO, "expirar", agora);
    }

    void estornar(Instant agora) {
        transitar(StatusPagamento.APROVADO, StatusPagamento.ESTORNADO, "estornar", agora);
    }

    private void transitar(StatusPagamento de, StatusPagamento para, String acao, Instant agora) {
        if (status != de) {
            throw new TransicaoInvalidaException("Pagamento", status, acao);
        }
        this.status = para;
        this.atualizadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public String getMpPreferenceId() {
        return mpPreferenceId;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public String getLinkPagamento() {
        return linkPagamento;
    }

    public int getTentativasEstorno() {
        return tentativasEstorno;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
