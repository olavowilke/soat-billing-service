package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Propriedades billing.mercadopago.* do cliente HTTP. So existem no modo real (ver {@link MercadoPagoConfig}).
 * Sempre sandbox: o access token e de uma conta de teste.
 *
 * <p>O segredo do webhook fica fora daqui: o controller le billing.mercadopago.webhook-secret, uma fonte so.
 */
@ConfigurationProperties(prefix = "billing.mercadopago")
public record MercadoPagoProperties(
        @DefaultValue("") String accessToken,
        @DefaultValue("https://api.mercadopago.com") String baseUrl,
        @DefaultValue("true") boolean sandbox,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT10S") Duration readTimeout) {

    public MercadoPagoProperties {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "billing.mercadopago.modo=real exige o access token do sandbox: defina BILLING_MERCADOPAGO_ACCESS_TOKEN");
        }
    }

    /** Sem o access token: o record geraria um toString com o segredo e iria para o log. */
    @Override
    public String toString() {
        return "MercadoPagoProperties[baseUrl=" + baseUrl + ", sandbox=" + sandbox + ", connectTimeout="
                + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
