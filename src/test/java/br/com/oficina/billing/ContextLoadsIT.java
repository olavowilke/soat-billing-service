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
}
