package br.com.oficina.billing.shared.infrastructure;

import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void recursoNaoEncontradoViraNotFound() {
        var resposta = handler.handleRecursoNaoEncontrado(new RecursoNaoEncontradoException("Orcamento", 7));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().message()).isEqualTo("Orcamento não encontrado(a) com id: 7");
    }

    @Test
    void recursoNaoEncontradoAceitaMensagemSimples() {
        var resposta = handler.handleRecursoNaoEncontrado(new RecursoNaoEncontradoException("sumiu"));

        assertThat(resposta.getBody().message()).isEqualTo("sumiu");
    }

    @Test
    void validacaoListaOsCamposInvalidos() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "alvo");
        binding.addError(new org.springframework.validation.FieldError("alvo", "valor", "deve ser positivo"));
        var metodo = getClass().getDeclaredMethod("parametro", String.class);
        var ex = new MethodArgumentNotValidException(new MethodParameter(metodo, 0), binding);

        var resposta = handler.handleValidation(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody().fields()).hasSize(1);
        assertThat(resposta.getBody().fields().get(0).field()).isEqualTo("valor");
        assertThat(resposta.getBody().fields().get(0).message()).isEqualTo("deve ser positivo");
    }

    @Test
    void corpoIlegivelViraBadRequest() {
        var ex = new HttpMessageNotReadableException("ruim", new MockHttpInputMessage(new byte[0]));

        var resposta = handler.handleMessageNotReadable(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tipoDeParametroInvalidoViraBadRequest() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "id", null, null);

        var resposta = handler.handleTypeMismatch(ex);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().message()).isEqualTo("Parâmetro inválido: id");
    }

    @Test
    void argumentoIlegalViraBadRequest() {
        var resposta = handler.handleIllegalArgument(new IllegalArgumentException("valor ruim"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().message()).isEqualTo("valor ruim");
    }

    @Test
    void estadoIlegalViraConflict() {
        var resposta = handler.handleIllegalState(new IllegalStateException("ja pago"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().message()).isEqualTo("ja pago");
    }

    @Test
    void erroInesperadoViraInternalServerErrorSemVazarDetalhe() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var nivel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        var resposta = handler.handleGeneric(new RuntimeException("segredo interno"));
        logger.setLevel(nivel);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resposta.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @SuppressWarnings("unused")
    private void parametro(String valor) {
    }
}
