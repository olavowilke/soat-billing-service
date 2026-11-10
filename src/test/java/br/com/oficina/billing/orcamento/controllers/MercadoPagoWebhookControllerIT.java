package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** No contexto padrao dos testes (modo fake) a rota do webhook nao existe. */
class MercadoPagoWebhookControllerIT extends BillingIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    void webhookRetorna404NoModoFake() {
        var resposta = http.postForEntity("/public/webhooks/mercadopago?type=payment&data.id=1", null, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
