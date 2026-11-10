package br.com.oficina.billing.shared.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing Service")
                        .description("Orçamentos, pagamentos via Mercado Pago (sandbox) e estornos.")
                        .version("1.0.0"))
                .servers(List.of(new Server().url("/api/billing").description("Billing Service")));
    }
}
