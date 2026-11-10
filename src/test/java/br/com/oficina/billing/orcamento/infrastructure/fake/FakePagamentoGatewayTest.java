package br.com.oficina.billing.orcamento.infrastructure.fake;

import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePagamentoGatewayTest {

    private final UUID pagamentoId = UUID.randomUUID();

    @Test
    void criaCobrancaComLinkDoEndpointFake() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", false);

        var cobranca = gateway.criarCobranca(pagamentoId, "Orcamento", new BigDecimal("10.00"));

        assertThat(cobranca.linkPagamento())
                .isEqualTo("http://localhost:8080/api/billing/public/fake-pagamentos/" + pagamentoId);
        assertThat(cobranca.preferenceId()).isNotBlank();
    }

    @Test
    void linkIgnoraBarraFinalDaUrlPublica() {
        var gateway = new FakePagamentoGateway("http://localhost:8080/", false);

        assertThat(gateway.criarCobranca(pagamentoId, "x", BigDecimal.ONE).linkPagamento())
                .startsWith("http://localhost:8080/api/billing/");
    }

    @Test
    void estornoFalhaQuandoConfigurado() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", true);

        assertThatThrownBy(() -> gateway.estornar("mp-1", pagamentoId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void estornoPassaPorPadrao() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", false);

        gateway.estornar("mp-1", pagamentoId);
    }

    @Test
    void consultaDevolvePendenteSemRegistro() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", false);

        var r = gateway.consultar(FakePagamentoGateway.mpPaymentIdDe(pagamentoId));

        assertThat(r.situacao()).isEqualTo(SituacaoPagamentoExterno.PENDENTE);
        assertThat(r.pagamentoId()).isEqualTo(pagamentoId);
    }

    @Test
    void consultaDevolveSituacaoRegistrada() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", false);

        gateway.registrarSituacao(pagamentoId, SituacaoPagamentoExterno.APROVADO);

        var r = gateway.consultar(FakePagamentoGateway.mpPaymentIdDe(pagamentoId));
        assertThat(r.situacao()).isEqualTo(SituacaoPagamentoExterno.APROVADO);
        assertThat(r.mpPaymentId()).isEqualTo("fake-" + pagamentoId);
    }

    @Test
    void consultaDeIdentificadorEstranhoFalha() {
        var gateway = new FakePagamentoGateway("http://localhost:8080", false);

        assertThatThrownBy(() -> gateway.consultar("outro-1")).isInstanceOf(IllegalArgumentException.class);
    }
}
