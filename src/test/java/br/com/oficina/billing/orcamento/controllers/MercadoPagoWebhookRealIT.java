package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.shared.BillingIT;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexto completo em billing.mercadopago.modo=real: confere a ligacao das propriedades, o gateway real,
 * o controller do webhook, o TransactionTemplate e o banco. O Mercado Pago e um WireMock.
 *
 * <p>Este contexto e diferente do dos outros ITs, entao o Spring cria um contexto proprio. Para ele NAO consumir a
 * fila de comandos compartilhada (e roubar mensagens dos outros ITs), usa uma fila de comandos so dele.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "billing.filas.comandos=" + MercadoPagoWebhookRealIT.FILA_COMANDOS_PROPRIA)
class MercadoPagoWebhookRealIT extends BillingIT {

    private static final String SEGREDO = "segredo-do-it";
    static final String FILA_COMANDOS_PROPRIA = "oficina-test-billing-commands-real";

    private static final WireMockServer mp = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        mp.start();
    }

    @DynamicPropertySource
    static void modoReal(DynamicPropertyRegistry registry) {
        registry.add("billing.mercadopago.modo", () -> "real");
        registry.add("billing.mercadopago.access-token", () -> "TEST-token-it");
        registry.add("billing.mercadopago.webhook-secret", () -> SEGREDO);
        registry.add("billing.mercadopago.base-url", mp::baseUrl);
    }

    @AfterAll
    static void parar() {
        mp.stop();
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${billing.filas.comandos}")
    private String filaComandos;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from outbox");
        mp.resetAll();
    }

    private Orcamento aprovado() {
        Instant agora = Instant.now();
        Orcamento o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), "corr-" + UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Servico", 1,
                        new BigDecimal("100.00"))), agora);
        o.aprovar(agora, Duration.ofMinutes(30)).definirCobranca("pref", "https://pagar");
        return orcamentoGateway.salvar(o);
    }

    private static void mpDevolve(String mpId, String status, String externalReference) {
        mp.stubFor(get("/v1/payments/" + mpId).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":" + mpId + ",\"status\":\"" + status + "\",\"external_reference\":\""
                        + externalReference + "\"}")));
    }

    private static String assinatura(String dataId, String segredo, String requestId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifesto = "id:" + dataId + ";request-id:" + requestId + ";ts:1700000000;";
        return "ts=1700000000,v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    private org.springframework.http.ResponseEntity<String> webhook(String dataId, String segredo) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.add("x-request-id", "req-it");
        h.add("x-signature", assinatura(dataId, segredo, "req-it"));
        h.add("x-correlation-id", "id-da-requisicao-http");
        return http.exchange("/public/webhooks/mercadopago?type=payment&data.id=" + dataId, HttpMethod.POST,
                new HttpEntity<>(h), String.class);
    }

    private int linhas(Orcamento o) {
        return jdbc.queryForObject("select count(*) from outbox where saga_id = ?", Integer.class, o.getSagaId());
    }

    @Test
    void contextoRealNaoConsomeAFilaCompartilhada() {
        assertThat(filaComandos).isEqualTo(FILA_COMANDOS_PROPRIA).isNotEqualTo(FILA_COMANDOS);
    }

    @Test
    void webhookAssinadoConfirmaPagamentoComTransacaoEBancoReais() throws Exception {
        Orcamento o = aprovado();
        mpDevolve("7001", "approved", o.getPagamento().orElseThrow().getId().toString());

        var resposta = webhook("7001", SEGREDO);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Orcamento depois = orcamentoGateway.buscarPorId(o.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(depois.getPagamento().orElseThrow().getMpPaymentId()).isEqualTo("7001");
        assertThat(jdbc.queryForObject("select tipo from outbox where saga_id = ?", String.class, o.getSagaId()))
                .isEqualTo("PagamentoConfirmado");
        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class,
                o.getSagaId())).isEqualTo(o.getCorrelationId());
    }

    @Test
    void webhookRepetidoNaoPublicaDeNovo() throws Exception {
        Orcamento o = aprovado();
        mpDevolve("7002", "approved", o.getPagamento().orElseThrow().getId().toString());
        webhook("7002", SEGREDO);

        var resposta = webhook("7002", SEGREDO);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(linhas(o)).isEqualTo(1);
    }

    @Test
    void assinaturaInvalidaRetorna401SemMudarNada(CapturedOutput saida) throws Exception {
        Orcamento o = aprovado();
        mpDevolve("7003", "approved", o.getPagamento().orElseThrow().getId().toString());

        var resposta = webhook("7003", "segredo-errado");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(orcamentoGateway.buscarPorId(o.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.APROVADO);
        assertThat(linhas(o)).isZero();
        assertThat(saida.getOut()).contains("WARN").contains("assinatura invalida");
    }

    @Test
    void pagamentoDeOutroSistemaRetorna200(CapturedOutput saida) throws Exception {
        mpDevolve("7004", "approved", "pedido-123");

        var resposta = webhook("7004", SEGREDO);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saida.getOut()).contains("WARN").contains("mpPaymentId=7004");
    }

    @Test
    void mercadoPagoForaDoArRetorna502(CapturedOutput saida) throws Exception {
        mp.stubFor(get("/v1/payments/7005").willReturn(aResponse().withStatus(500)));

        var resposta = webhook("7005", SEGREDO);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(saida.getOut()).contains("WARN").contains("falha ao consultar pagamento");
    }
}
