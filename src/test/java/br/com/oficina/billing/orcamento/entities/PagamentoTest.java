package br.com.oficina.billing.orcamento.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PagamentoTest {

    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    private Pagamento novo() {
        return Pagamento.criar(new BigDecimal("350.00"), T0.plusSeconds(1800), T0);
    }

    @Test
    void criarComecaPendenteSemCobranca() {
        var p = novo();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getValor()).isEqualByComparingTo("350.00");
        assertThat(p.getLinkPagamento()).isNull();
        assertThat(p.getMpPreferenceId()).isNull();
        assertThat(p.getMpPaymentId()).isNull();
        assertThat(p.getTentativasEstorno()).isZero();
    }

    @Test
    void definirCobrancaGuardaPreferenciaELink() {
        var p = novo();
        p.definirCobranca("PREF-1", "https://pay/1");
        assertThat(p.getMpPreferenceId()).isEqualTo("PREF-1");
        assertThat(p.getLinkPagamento()).isEqualTo("https://pay/1");
    }

    @Test
    void vencidoSoNoPrazoOuDepois() {
        var p = novo();
        assertThat(p.vencido(T0.plusSeconds(1799))).isFalse();
        assertThat(p.vencido(T0.plusSeconds(1800))).isTrue();
        assertThat(p.vencido(T0.plusSeconds(1801))).isTrue();
    }

    @Test
    void registrarFalhaEstornoIncrementa() {
        var p = novo();
        p.registrarFalhaEstorno();
        p.registrarFalhaEstorno();
        assertThat(p.getTentativasEstorno()).isEqualTo(2);
    }

    @Test
    void reconstituirPreservaTodosOsCampos() {
        var id = UUID.randomUUID();
        var p = Pagamento.reconstituir(id, StatusPagamento.APROVADO, new BigDecimal("10.00"), T0, "PREF", "PAY",
                "link", 3, T0, T0.plusSeconds(9));
        assertThat(p.getId()).isEqualTo(id);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getValor()).isEqualByComparingTo("10.00");
        assertThat(p.getExpiraEm()).isEqualTo(T0);
        assertThat(p.getMpPreferenceId()).isEqualTo("PREF");
        assertThat(p.getMpPaymentId()).isEqualTo("PAY");
        assertThat(p.getLinkPagamento()).isEqualTo("link");
        assertThat(p.getTentativasEstorno()).isEqualTo(3);
        assertThat(p.getCriadoEm()).isEqualTo(T0);
        assertThat(p.getAtualizadoEm()).isEqualTo(T0.plusSeconds(9));
    }
}
