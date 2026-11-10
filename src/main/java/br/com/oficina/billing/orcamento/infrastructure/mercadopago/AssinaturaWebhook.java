package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Confere o cabecalho x-signature do webhook do Mercado Pago.
 *
 * <p>O cabecalho tem a forma {@code ts=<ts>,v1=<hex>}. O manifesto assinado e
 * {@code id:<data.id em minusculas>;request-id:<x-request-id>;ts:<ts>;}, com HMAC-SHA256 e o segredo do webhook.
 */
public final class AssinaturaWebhook {

    private AssinaturaWebhook() {
    }

    public static boolean valida(String xSignature, String xRequestId, String dataId, String segredo) {
        if (vazio(xSignature) || vazio(xRequestId) || vazio(dataId) || vazio(segredo)) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            int igual = parte.indexOf('=');
            if (igual < 0) {
                continue;
            }
            String chave = parte.substring(0, igual).trim();
            String valor = parte.substring(igual + 1).trim();
            if (chave.equals("ts")) {
                ts = valor;
            } else if (chave.equals("v1")) {
                v1 = valor;
            }
        }
        if (vazio(ts) || vazio(v1)) {
            return false;
        }
        String manifesto = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        byte[] esperado = hmacHex(manifesto, segredo).getBytes(StandardCharsets.US_ASCII);
        byte[] recebido = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        // Tempo constante: nao revela quantos caracteres da assinatura estavam certos.
        return MessageDigest.isEqual(esperado, recebido);
    }

    private static String hmacHex(String texto, String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponivel", e);
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
