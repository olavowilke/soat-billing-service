package br.com.oficina.billing.orcamento.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrcamentoTest {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");
    private static final Duration PRAZO = Duration.ofMinutes(30);

    private final UUID clienteId = UUID.randomUUID();
    private Orcamento orc;

    @BeforeEach
    void setUp() {
        orc = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), clienteId, itens(), T0);
    }

    private static List<ItemOrcamento> itens() {
        return List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 2, new BigDecimal("100.00")),
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 1, new BigDecimal("150.00")));
    }

    @Test
    void totalSomaSubtotais() {
        assertThat(orc.getValorTotal()).isEqualByComparingTo("350.00");
        assertThat(orc.getValorTotal().scale()).isEqualTo(2);
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(orc.getPagamento()).isEmpty();
        assertThat(orc.getVersao()).isZero();
        assertThat(orc.getCriadoEm()).isEqualTo(T0);
        assertThat(orc.pertenceAo(clienteId)).isTrue();
        assertThat(orc.pertenceAo(UUID.randomUUID())).isFalse();
    }

    @Test
    void itensSaoImutaveis() {
        var lista = orc.getItens();
        var item = lista.get(0);
        assertThatThrownBy(() -> lista.add(item)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void gerarSemItensFalha() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        List<ItemOrcamento> vazia = List.of();
        assertThatThrownBy(() -> Orcamento.gerar(osId, sagaId, clienteId, vazia, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Orcamento.gerar(osId, sagaId, clienteId, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gerarComTotalZeroFalha() {
        var osId = UUID.randomUUID();
        var sagaId = UUID.randomUUID();
        var zero = List.of(new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Brinde", 1, BigDecimal.ZERO));
        assertThatThrownBy(() -> Orcamento.gerar(osId, sagaId, clienteId, zero, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aprovarCriaPagamentoPendenteComPrazo() {
        var p = orc.aprovar(T0, PRAZO);
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getExpiraEm()).isEqualTo(T0.plus(PRAZO));
        assertThat(p.getValor()).isEqualByComparingTo("350.00");
        assertThat(orc.getPagamento()).containsSame(p);
    }

    @Test
    void aprovarDuasVezesFalha() {
        orc.aprovar(T0, PRAZO);
        assertThatThrownBy(() -> orc.aprovar(T0, PRAZO)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void recusarMarcaRecusado() {
        var depois = T0.plusSeconds(5);
        orc.recusar(depois);
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        assertThat(orc.getAtualizadoEm()).isEqualTo(depois);
        assertThatThrownBy(() -> orc.aprovar(depois, PRAZO)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void recusarAprovadoFalha() {
        orc.aprovar(T0, PRAZO);
        assertThatThrownBy(() -> orc.recusar(T0)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void confirmarPagamentoMarcaPago() {
        orc.aprovar(T0, PRAZO);
        orc.confirmarPagamento("MP-1", T0.plusSeconds(60));
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        var p = orc.getPagamento().orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getMpPaymentId()).isEqualTo("MP-1");
    }

    @Test
    void confirmarSemAprovarFalha() {
        assertThatThrownBy(() -> orc.confirmarPagamento("MP-1", T0)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void rejeitarPagamentoCancelaOrcamento() {
        orc.aprovar(T0, PRAZO);
        orc.rejeitarPagamento(T0.plusSeconds(60));
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(orc.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.REJEITADO);
    }

    @Test
    void expirarPagamentoCancelaOrcamento() {
        orc.aprovar(T0, PRAZO);
        orc.expirarPagamento(T0.plus(PRAZO));
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(orc.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
    }

    @Test
    void rejeitarEExpirarDepoisDePagoFalham() {
        orc.aprovar(T0, PRAZO);
        orc.confirmarPagamento("MP-1", T0);
        assertThatThrownBy(() -> orc.rejeitarPagamento(T0)).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> orc.expirarPagamento(T0)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void estornarSoDePago() {
        assertThatThrownBy(() -> orc.estornar(T0)).isInstanceOf(TransicaoInvalidaException.class);
        orc.aprovar(T0, PRAZO);
        assertThatThrownBy(() -> orc.estornar(T0)).isInstanceOf(TransicaoInvalidaException.class);
        orc.confirmarPagamento("MP-1", T0);
        orc.estornar(T0.plusSeconds(120));
        assertThat(orc.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(orc.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThatThrownBy(() -> orc.estornar(T0)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void reconstituirPreservaTodosOsCampos() {
        orc.aprovar(T0, PRAZO);
        var p = orc.getPagamento().orElseThrow();
        p.definirCobranca("PREF-1", "https://pay/1");
        var copia = Orcamento.reconstituir(orc.getId(), orc.getOsId(), orc.getSagaId(), orc.getClienteId(),
                orc.getStatus(), orc.getItens(), orc.getValorTotal(), p, 7L, orc.getCriadoEm(), orc.getAtualizadoEm());
        assertThat(copia.getId()).isEqualTo(orc.getId());
        assertThat(copia.getOsId()).isEqualTo(orc.getOsId());
        assertThat(copia.getSagaId()).isEqualTo(orc.getSagaId());
        assertThat(copia.getClienteId()).isEqualTo(clienteId);
        assertThat(copia.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(copia.getItens()).hasSize(2);
        assertThat(copia.getValorTotal()).isEqualByComparingTo("350.00");
        assertThat(copia.getPagamento()).containsSame(p);
        assertThat(copia.getVersao()).isEqualTo(7L);
        assertThat(copia.getAtualizadoEm()).isEqualTo(orc.getAtualizadoEm());
    }
}
