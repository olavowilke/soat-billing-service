package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import br.com.oficina.billing.orcamento.gateways.CobrancaCriada;
import br.com.oficina.billing.orcamento.gateways.ProvedorPagamentoIndisponivelException;
import br.com.oficina.billing.orcamento.gateways.ResultadoConsultaPagamento;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoPagamentoGatewayTest {

    private static final String TOKEN = "TEST-token-sandbox";
    private static final UUID PAGAMENTO = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PREFERENCIA = """
            {"id":"pref-1","init_point":"https://mp/prod","sandbox_init_point":"https://mp/sandbox"}
            """;

    @RegisterExtension
    static WireMockExtension mp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort()).build();

    private final Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoPagamentoGateway.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.setAdditive(false);
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
        logger.setAdditive(true);
    }

    private MercadoPagoPagamentoGateway gateway(boolean sandbox, Duration leitura, String urlPublica) {
        var props = new MercadoPagoProperties(TOKEN, mp.baseUrl(), sandbox, Duration.ofSeconds(2), leitura);
        return new MercadoPagoPagamentoGateway(props, urlPublica);
    }

    private MercadoPagoPagamentoGateway gateway(boolean sandbox) {
        return gateway(sandbox, Duration.ofSeconds(5), "https://billing.exemplo.com");
    }

    private static void responderJson(String url, int status, String corpo, boolean post) {
        var resposta = aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(corpo);
        mp.stubFor(post ? post(url).willReturn(resposta) : get(url).willReturn(resposta));
    }

    @Test
    void criarCobrancaEnviaExternalReferenceENotificationUrl() {
        responderJson("/checkout/preferences", 201, PREFERENCIA, true);

        CobrancaCriada cobranca = gateway(true).criarCobranca(PAGAMENTO, "Orcamento da OS 1",
                new BigDecimal("350.50"));

        assertThat(cobranca.preferenceId()).isEqualTo("pref-1");
        assertThat(cobranca.linkPagamento()).isEqualTo("https://mp/sandbox");
        mp.verify(postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(equalToJson("""
                        {
                          "items": [{"title": "Orcamento da OS 1", "quantity": 1, "unit_price": 350.50,
                                     "currency_id": "BRL"}],
                          "external_reference": "11111111-2222-3333-4444-555555555555",
                          "notification_url": "https://billing.exemplo.com/api/billing/public/webhooks/mercadopago"
                        }
                        """, false, false)));
    }

    @Test
    void semSandboxUsaInitPoint() {
        responderJson("/checkout/preferences", 201, PREFERENCIA, true);

        assertThat(gateway(false).criarCobranca(PAGAMENTO, "x", BigDecimal.TEN).linkPagamento())
                .isEqualTo("https://mp/prod");
    }

    @Test
    void urlPublicaComBarraNoFinalNaoDuplicaBarra() {
        responderJson("/checkout/preferences", 201, PREFERENCIA, true);

        gateway(true, Duration.ofSeconds(5), "https://billing.exemplo.com/").criarCobranca(PAGAMENTO, "x",
                BigDecimal.ONE);

        mp.verify(postRequestedFor(urlEqualTo("/checkout/preferences")).withRequestBody(
                WireMock.containing("https://billing.exemplo.com/api/billing/public/webhooks/mercadopago")));
    }

    @Test
    void criarCobrancaSemLinkNaRespostaFalha() {
        responderJson("/checkout/preferences", 201, "{\"id\":\"pref-3\"}", true);

        assertThatThrownBy(() -> gateway(true).criarCobranca(PAGAMENTO, "x", BigDecimal.TEN))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class);
    }

    @ParameterizedTest
    @CsvSource({"approved,APROVADO", "rejected,REJEITADO", "cancelled,REJEITADO", "pending,PENDENTE",
            "in_process,PENDENTE", "authorized,PENDENTE", "refunded,PENDENTE"})
    void consultarMapeiaStatus(String statusMp, SituacaoPagamentoExterno esperado) {
        responderJson("/v1/payments/987", 200,
                "{\"id\":987,\"status\":\"" + statusMp + "\",\"external_reference\":\"" + PAGAMENTO + "\"}", false);

        ResultadoConsultaPagamento r = gateway(true).consultar("987");

        assertThat(r.pagamentoId()).isEqualTo(PAGAMENTO);
        assertThat(r.mpPaymentId()).isEqualTo("987");
        assertThat(r.situacao()).isEqualTo(esperado);
        mp.verify(getRequestedFor(urlEqualTo("/v1/payments/987"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    void consultarSemExternalReferenceValidaDevolvePagamentoNulo() {
        responderJson("/v1/payments/1", 200,
                "{\"id\":1,\"status\":\"approved\",\"external_reference\":\"nao-e-uuid\"}", false);
        responderJson("/v1/payments/2", 200, "{\"id\":2,\"status\":\"approved\"}", false);

        assertThat(gateway(true).consultar("1").pagamentoId()).isNull();
        assertThat(gateway(true).consultar("2").pagamentoId()).isNull();
    }

    @Test
    void estornoEnviaIdempotencyKey() {
        responderJson("/v1/payments/987/refunds", 201, "{\"id\":1,\"status\":\"approved\"}", true);

        gateway(true).estornar("987", PAGAMENTO);

        mp.verify(postRequestedFor(urlEqualTo("/v1/payments/987/refunds"))
                .withHeader("X-Idempotency-Key", equalTo(PAGAMENTO + "-987"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    void erroHttpDoProvedorViraExcecaoELogaACausa() {
        responderJson("/checkout/preferences", 500, "{\"message\":\"boom\"}", true);

        assertThatThrownBy(() -> gateway(true).criarCobranca(PAGAMENTO, "x", BigDecimal.TEN))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class)
                .hasMessageContaining("criar cobranca")
                .hasCauseInstanceOf(RestClientResponseException.class);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("criar cobranca").contains("500");
        });
    }

    @Test
    void erro4xxNaConsultaTambemViraExcecao() {
        responderJson("/v1/payments/404", 404, "{}", false);

        assertThatThrownBy(() -> gateway(true).consultar("404"))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class)
                .hasMessageContaining("consultar");
    }

    @Test
    void erroNoEstornoViraExcecao() {
        responderJson("/v1/payments/5/refunds", 400, "{}", true);

        assertThatThrownBy(() -> gateway(true).estornar("5", PAGAMENTO))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class)
                .hasMessageContaining("estornar");
    }

    @Test
    void timeoutDeLeituraViraExcecao() {
        mp.stubFor(get("/v1/payments/9").willReturn(aResponse().withStatus(200).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody("{}")));

        assertThatThrownBy(() -> gateway(true, Duration.ofMillis(300), "https://x").consultar("9"))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void corpoVazioNaConsultaFalha() {
        mp.stubFor(get("/v1/payments/8").willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> gateway(true).consultar("8"))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class);
    }
}
