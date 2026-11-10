package br.com.oficina.billing.shared.infrastructure;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only route that accepts POST only, to prove the 405 mapping. */
@RestController
@RequestMapping("/teste-erros")
class RotaSoPostFixture {

    @PostMapping
    void aceitar() {
    }
}
