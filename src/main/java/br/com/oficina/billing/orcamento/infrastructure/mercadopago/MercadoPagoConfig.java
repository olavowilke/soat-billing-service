package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades do Mercado Pago so no modo real. */
@Configuration
@ConditionalOnProperty(name = "billing.mercadopago.modo", havingValue = "real")
@EnableConfigurationProperties(MercadoPagoProperties.class)
class MercadoPagoConfig {
}
