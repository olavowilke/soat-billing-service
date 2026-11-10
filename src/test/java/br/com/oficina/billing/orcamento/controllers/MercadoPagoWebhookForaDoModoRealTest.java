package br.com.oficina.billing.orcamento.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com modo diferente de real o controller nem existe, entao a rota responde 404. */
@WebMvcTest(controllers = MercadoPagoWebhookController.class, properties = "billing.mercadopago.modo=fake")
class MercadoPagoWebhookForaDoModoRealTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void webhookRetorna404ForaDoModoReal() throws Exception {
        mvc.perform(post("/public/webhooks/mercadopago").param("type", "payment").param("data.id", "1"))
                .andExpect(status().isNotFound());
    }
}
