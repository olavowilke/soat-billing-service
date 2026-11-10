package br.com.oficina.billing.shared.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void documentacaoApontaParaOContextPathDoBilling() {
        var openApi = new OpenApiConfig().openAPI();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Billing Service");
        assertThat(openApi.getServers()).extracting("url").containsExactly("/api/billing");
    }
}
