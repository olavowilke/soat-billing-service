package br.com.oficina.billing.orcamento.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com modo diferente de fake o controller nem existe, entao a rota responde 404. */
@WebMvcTest(controllers = FakePagamentoController.class, properties = "billing.mercadopago.modo=real")
class FakePagamentoControllerForaDoModoFakeTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fakeEndpointRetorna404ForaDoModoFake() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/public/fake-pagamentos/" + id + "/aprovar")).andExpect(status().isNotFound());
        mvc.perform(post("/public/fake-pagamentos/" + id + "/rejeitar")).andExpect(status().isNotFound());
    }
}
