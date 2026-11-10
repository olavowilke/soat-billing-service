package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Liga as propriedades e o gateway reais sem subir o contexto da aplicacao (sem banco, sem SQS). */
class MercadoPagoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MercadoPagoConfig.class, MercadoPagoPagamentoGateway.class)
            .withPropertyValues("billing.url-publica=https://billing.exemplo.com");

    @Test
    void modoRealLigaPropriedadesEUmUnicoGateway() {
        runner.withPropertyValues("billing.mercadopago.modo=real",
                "billing.mercadopago.access-token=TEST-token",
                "billing.mercadopago.base-url=http://localhost:9999",
                "billing.mercadopago.sandbox=false",
                "billing.mercadopago.connect-timeout=PT1S",
                "billing.mercadopago.read-timeout=PT2S").run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto.getBeansOfType(PagamentoGateway.class)).hasSize(1);
                    assertThat(contexto).hasSingleBean(MercadoPagoPagamentoGateway.class);
                    MercadoPagoProperties props = contexto.getBean(MercadoPagoProperties.class);
                    assertThat(props.accessToken()).isEqualTo("TEST-token");
                    assertThat(props.baseUrl()).isEqualTo("http://localhost:9999");
                    assertThat(props.sandbox()).isFalse();
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void modoRealUsaOsPadroesDoBrief() {
        runner.withPropertyValues("billing.mercadopago.modo=real", "billing.mercadopago.access-token=TEST-token")
                .run(contexto -> {
                    MercadoPagoProperties props = contexto.getBean(MercadoPagoProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("https://api.mercadopago.com");
                    assertThat(props.sandbox()).isTrue();
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void modoRealSemAccessTokenFalhaNaPartida() {
        runner.withPropertyValues("billing.mercadopago.modo=real", "billing.mercadopago.access-token=")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("BILLING_MERCADOPAGO_ACCESS_TOKEN");
                });
    }

    @Test
    void modoFakeNaoCriaGatewayNemPropriedadesReais() {
        runner.withPropertyValues("billing.mercadopago.modo=fake").run(contexto -> {
            assertThat(contexto).hasNotFailed();
            assertThat(contexto).doesNotHaveBean(MercadoPagoPagamentoGateway.class);
            assertThat(contexto).doesNotHaveBean(MercadoPagoProperties.class);
        });
    }

    @Test
    void toStringNaoMostraSegredos() {
        var props = new MercadoPagoProperties("TEST-super-secreto", "http://x", true, Duration.ofSeconds(1),
                Duration.ofSeconds(2));

        assertThat(props.toString()).doesNotContain("TEST-super-secreto").contains("http://x");
    }
}
