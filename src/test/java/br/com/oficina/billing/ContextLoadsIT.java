package br.com.oficina.billing;

import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ContextLoadsIT extends BillingIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextoSobe() {
        var resposta = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resposta.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void sondaDeProntidaoResponde200() {
        var resposta = restTemplate.getForEntity("/actuator/health/readiness", String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void sondaDeVivacidadeResponde200() {
        var resposta = restTemplate.getForEntity("/actuator/health/liveness", String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void saudeNaoExpoeDetalhes() {
        var resposta = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getBody()).doesNotContain("components");
    }
}
