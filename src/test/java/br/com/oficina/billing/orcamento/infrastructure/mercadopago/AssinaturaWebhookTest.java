package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaWebhookTest {

    private static final String SEGREDO = "segredo-de-teste";
    private static final String REQUEST_ID = "req-123";
    private static final String TS = "1742505638683";

    /** Calcula o HMAC como o Mercado Pago documenta, para o teste nao depender da classe testada. */
    private static String hmac(String manifesto, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    private static String assinatura(String dataIdEmMinusculas) throws Exception {
        String v1 = hmac("id:" + dataIdEmMinusculas + ";request-id:" + REQUEST_ID + ";ts:" + TS + ";", SEGREDO);
        return "ts=" + TS + ",v1=" + v1;
    }

    @Test
    void assinaturaValidaAceita() throws Exception {
        assertThat(AssinaturaWebhook.valida(assinatura("123456"), REQUEST_ID, "123456", SEGREDO)).isTrue();
    }

    @Test
    void dataIdEmMaiusculasEhNormalizadoParaMinusculas() throws Exception {
        assertThat(AssinaturaWebhook.valida(assinatura("abc123"), REQUEST_ID, "ABC123", SEGREDO)).isTrue();
    }

    @Test
    void aceitaPartesEmQualquerOrdemEComEspacos() throws Exception {
        String v1 = hmac("id:1;request-id:" + REQUEST_ID + ";ts:" + TS + ";", SEGREDO);
        assertThat(AssinaturaWebhook.valida("v1=" + v1 + ", ts=" + TS, REQUEST_ID, "1", SEGREDO)).isTrue();
    }

    @Test
    void hexEmMaiusculasEhAceito() throws Exception {
        String valida = assinatura("1");
        String maiuscula = valida.substring(0, valida.indexOf("v1=") + 3)
                + valida.substring(valida.indexOf("v1=") + 3).toUpperCase();
        assertThat(AssinaturaWebhook.valida(maiuscula, REQUEST_ID, "1", SEGREDO)).isTrue();
    }

    @Test
    void segredoDiferenteRecusa() throws Exception {
        assertThat(AssinaturaWebhook.valida(assinatura("1"), REQUEST_ID, "1", "outro-segredo")).isFalse();
    }

    @Test
    void dataIdDiferenteRecusa() throws Exception {
        assertThat(AssinaturaWebhook.valida(assinatura("1"), REQUEST_ID, "2", SEGREDO)).isFalse();
    }

    @Test
    void requestIdDiferenteRecusa() throws Exception {
        assertThat(AssinaturaWebhook.valida(assinatura("1"), "outro-request", "1", SEGREDO)).isFalse();
    }

    @Test
    void assinaturaAdulteradaRecusa() throws Exception {
        String valida = assinatura("1");
        String adulterada = valida.substring(0, valida.length() - 1) + (valida.endsWith("0") ? "1" : "0");
        assertThat(AssinaturaWebhook.valida(adulterada, REQUEST_ID, "1", SEGREDO)).isFalse();
    }

    @Test
    void cabecalhosAusentesOuEmBrancoRecusam() throws Exception {
        String valida = assinatura("1");
        assertThat(AssinaturaWebhook.valida(null, REQUEST_ID, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida(" ", REQUEST_ID, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida(valida, null, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida(valida, " ", "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida(valida, REQUEST_ID, null, SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida(valida, REQUEST_ID, "1", null)).isFalse();
    }

    @Test
    void segredoVazioRecusa() throws Exception {
        String v1 = hmac("id:1;request-id:" + REQUEST_ID + ";ts:" + TS + ";", "x");
        assertThat(AssinaturaWebhook.valida("ts=" + TS + ",v1=" + v1, REQUEST_ID, "1", "")).isFalse();
    }

    @Test
    void assinaturaMalformadaRecusa() {
        assertThat(AssinaturaWebhook.valida("lixo", REQUEST_ID, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida("ts=" + TS, REQUEST_ID, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida("v1=abc", REQUEST_ID, "1", SEGREDO)).isFalse();
        assertThat(AssinaturaWebhook.valida("ts=" + TS + ",v1=nao-e-hex", REQUEST_ID, "1", SEGREDO)).isFalse();
    }
}
