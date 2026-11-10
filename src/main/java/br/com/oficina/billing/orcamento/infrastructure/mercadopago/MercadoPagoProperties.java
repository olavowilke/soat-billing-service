package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Propriedades billing.mercadopago.*. Sempre sandbox: o access token e de uma conta de teste. */
@ConfigurationProperties(prefix = "billing.mercadopago")
public record MercadoPagoProperties(
        @DefaultValue("fake") String modo,
        @DefaultValue("") String accessToken,
        @DefaultValue("") String webhookSecret,
        @DefaultValue("https://api.mercadopago.com") String baseUrl,
        @DefaultValue("true") boolean sandbox,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT10S") Duration readTimeout) {
}
