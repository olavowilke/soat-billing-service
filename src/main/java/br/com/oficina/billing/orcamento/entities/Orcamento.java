package br.com.oficina.billing.orcamento.entities;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Orcamento {

    private final UUID id;
    private final UUID osId;
    private final UUID sagaId;
    private final String correlationId;
    private final UUID clienteId;
    private StatusOrcamento status;
    private final List<ItemOrcamento> itens;
    private final BigDecimal valorTotal;
    private Pagamento pagamento;
    private final long versao;
    private final Instant criadoEm;
    private Instant atualizadoEm;

    private Orcamento(UUID id, UUID osId, UUID sagaId, String correlationId, UUID clienteId, StatusOrcamento status,
            List<ItemOrcamento> itens, BigDecimal valorTotal, Pagamento pagamento, long versao, Instant criadoEm,
            Instant atualizadoEm) {
        this.id = id;
        this.osId = osId;
        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.clienteId = clienteId;
        this.status = status;
        this.itens = List.copyOf(itens);
        this.valorTotal = valorTotal;
        this.pagamento = pagamento;
        this.versao = versao;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static Orcamento gerar(UUID osId, UUID sagaId, String correlationId, UUID clienteId,
            List<ItemOrcamento> itens, Instant agora) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("orcamento exige ao menos um item");
        }
        BigDecimal total = itens.stream().map(ItemOrcamento::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("valor total deve ser maior que zero");
        }
        return new Orcamento(UUID.randomUUID(), osId, sagaId, correlationId, clienteId, StatusOrcamento.GERADO, itens, total, null, 0L,
                agora, agora);
    }

    public static Orcamento reconstituir(UUID id, UUID osId, UUID sagaId, String correlationId,
            UUID clienteId, StatusOrcamento status,
            List<ItemOrcamento> itens, BigDecimal valorTotal, Pagamento pagamento, long versao, Instant criadoEm,
            Instant atualizadoEm) {
        return new Orcamento(id, osId, sagaId, correlationId, clienteId, status, itens, valorTotal, pagamento, versao, criadoEm,
                atualizadoEm);
    }

    public Pagamento aprovar(Instant agora, Duration prazoPagamento) {
        exigir(StatusOrcamento.GERADO, "aprovar");
        this.pagamento = Pagamento.criar(valorTotal, agora.plus(prazoPagamento), agora);
        mudar(StatusOrcamento.APROVADO, agora);
        return pagamento;
    }

    public void recusar(Instant agora) {
        exigir(StatusOrcamento.GERADO, "recusar");
        mudar(StatusOrcamento.RECUSADO, agora);
    }

    public void confirmarPagamento(String mpPaymentId, Instant agora) {
        exigir(StatusOrcamento.APROVADO, "confirmarPagamento");
        pagamento.aprovar(mpPaymentId, agora);
        mudar(StatusOrcamento.PAGO, agora);
    }

    public void rejeitarPagamento(Instant agora) {
        exigir(StatusOrcamento.APROVADO, "rejeitarPagamento");
        pagamento.rejeitar(agora);
        mudar(StatusOrcamento.CANCELADO, agora);
    }

    public void expirarPagamento(Instant agora) {
        exigir(StatusOrcamento.APROVADO, "expirarPagamento");
        pagamento.expirar(agora);
        mudar(StatusOrcamento.CANCELADO, agora);
    }

    public void estornar(Instant agora) {
        exigir(StatusOrcamento.PAGO, "estornar");
        pagamento.estornar(agora);
        mudar(StatusOrcamento.ESTORNADO, agora);
    }

    public boolean pertenceAo(UUID clienteId) {
        return this.clienteId.equals(clienteId);
    }

    private void exigir(StatusOrcamento esperado, String acao) {
        if (status != esperado || (esperado != StatusOrcamento.GERADO && pagamento == null)) {
            throw new TransicaoInvalidaException("Orcamento", status, acao);
        }
    }

    private void mudar(StatusOrcamento novo, Instant agora) {
        this.status = novo;
        this.atualizadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOsId() {
        return osId;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    /** Id de correlacao da saga: toda resposta deste orcamento usa o mesmo. */
    public String getCorrelationId() {
        return correlationId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public StatusOrcamento getStatus() {
        return status;
    }

    public List<ItemOrcamento> getItens() {
        return itens;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public Optional<Pagamento> getPagamento() {
        return Optional.ofNullable(pagamento);
    }

    public long getVersao() {
        return versao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
