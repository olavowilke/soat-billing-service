package br.com.oficina.billing.shared.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o {@link RequestLoggingFilter}: uma linha de entrada e uma de saída
 * por requisição, com o status da resposta, e silêncio para as rotas de
 * infraestrutura (probes do kubelet e monitor sintético).
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filtro = new RequestLoggingFilter();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void capturarOsLogs() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void soltarOApendice() {
        logger.detachAppender(appender);
    }

    private List<String> mensagens() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void deveLogarEntradaESaidaComOStatusDaResposta() throws Exception {
        var requisicao = new MockHttpServletRequest("GET", "/api/billing/orcamentos/42");
        var resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) ->
                ((MockHttpServletResponse) res).setStatus(200));

        assertThat(mensagens()).hasSize(2);
        assertThat(mensagens().get(0)).isEqualTo("Requisicao recebida: GET /api/billing/orcamentos/42");
        assertThat(mensagens().get(1)).startsWith("Requisicao concluida: GET /api/billing/orcamentos/42 -> 200");
    }

    @Test
    void deveLogarOStatusDeErroQuandoARequisicaoFalha() throws Exception {
        var requisicao = new MockHttpServletRequest("POST", "/api/clientes");
        var resposta = new MockHttpServletResponse();

        filtro.doFilter(requisicao, resposta, (req, res) ->
                ((MockHttpServletResponse) res).setStatus(403));

        assertThat(mensagens().get(1)).startsWith("Requisicao concluida: POST /api/clientes -> 403");
    }

    @Test
    void deveLogarASaidaMesmoQuandoACadeiaLancaExcecao() {
        var requisicao = new MockHttpServletRequest("GET", "/api/clientes");
        var resposta = new MockHttpServletResponse();

        try {
            filtro.doFilter(requisicao, resposta, (req, res) -> {
                throw new IllegalStateException("falha no controller");
            });
        } catch (Exception esperada) {
            // a exceção segue para o tratador global; aqui só garantimos o log
        }

        assertThat(mensagens()).hasSize(2);
        assertThat(mensagens().get(1)).startsWith("Requisicao concluida: GET /api/clientes ->");
    }

    @Test
    void naoDeveLogarAsRotasDeInfraestrutura() throws Exception {
        // O kubelet chama as probes a cada poucos segundos e o monitor
        // sintetico a cada 5 minutos. Logar isso afoga os logs de negocio e
        // vira custo de ingestao no New Relic sem nenhum valor.
        for (String rota : List.of("/api/actuator/health", "/api/actuator/health/readiness",
                "/api/actuator/prometheus")) {
            var requisicao = new MockHttpServletRequest("GET", rota);
            filtro.doFilter(requisicao, new MockHttpServletResponse(), (req, res) -> {});
        }

        assertThat(mensagens()).isEmpty();
    }
}
