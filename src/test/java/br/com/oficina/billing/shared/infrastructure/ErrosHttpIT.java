package br.com.oficina.billing.shared.infrastructure;

import br.com.oficina.billing.shared.BillingIT;
import br.com.oficina.billing.shared.presenters.ApiError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ErrosHttpIT extends BillingIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void caminhoDesconhecidoRetorna404SemErroNoLog(CapturedOutput saida) {
        var resposta = restTemplate.getForEntity("/nao-existe", ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().status()).isEqualTo(404);
        assertThat(saida.getOut()).doesNotContain("ERROR");
    }

    @Test
    void metodoNaoSuportadoRetorna405SemErroNoLog(CapturedOutput saida) {
        var resposta = restTemplate.getForEntity("/teste-erros", ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resposta.getBody().status()).isEqualTo(405);
        assertThat(saida.getOut()).doesNotContain("ERROR");
    }
}
