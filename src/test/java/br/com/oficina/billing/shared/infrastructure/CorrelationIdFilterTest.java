package br.com.oficina.billing.shared.infrastructure;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o {@link CorrelationIdFilter}: correlação de requisições via MDC,
 * reaproveitamento do X-Correlation-Id vindo do API Gateway e limpeza do MDC
 * ao final da requisição (mesmo em caso de exceção na cadeia de filtros).
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @Test
    void deveReutilizarOCorrelationIdRecebidoDoGateway() throws Exception {
        var requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Correlation-Id", "req-abc");
        var resposta = new MockHttpServletResponse();
        var capturado = new AtomicReference<String>();

        filtro.doFilter(requisicao, resposta, (req, res) -> capturado.set(MDC.get("correlationId")));

        assertThat(capturado.get()).isEqualTo("req-abc");
        assertThat(resposta.getHeader("X-Correlation-Id")).isEqualTo("req-abc");
        assertThat(MDC.get("correlationId")).isNull(); // limpou o MDC no finally
    }

    @Test
    void deveGerarUmCorrelationIdQuandoNaoVemNoCabecalho() throws Exception {
        var requisicao = new MockHttpServletRequest();
        var resposta = new MockHttpServletResponse();
        var capturado = new AtomicReference<String>();

        filtro.doFilter(requisicao, resposta, (req, res) -> capturado.set(MDC.get("correlationId")));

        assertThat(capturado.get()).isNotNull();
        assertThat(resposta.getHeader("X-Correlation-Id")).isEqualTo(capturado.get());
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void devePopularClienteIdNoMdcApartirDoCabecalhoDoGateway() throws Exception {
        var requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Cliente-Id", "cliente-42");
        var resposta = new MockHttpServletResponse();
        var capturado = new AtomicReference<String>();

        filtro.doFilter(requisicao, resposta, (req, res) -> capturado.set(MDC.get("clienteId")));

        assertThat(capturado.get()).isEqualTo("cliente-42");
        assertThat(MDC.get("clienteId")).isNull(); // limpou o MDC no finally
    }

    @Test
    void deveLimparOMdcMesmoQuandoACadeiaDeFiltrosLancaExcecao() {
        var requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Correlation-Id", "req-com-erro");
        requisicao.addHeader("X-Cliente-Id", "cliente-99");
        var resposta = new MockHttpServletResponse();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                filtro.doFilter(requisicao, resposta, (req, res) -> {
                    throw new IllegalStateException("falha simulada na cadeia de filtros");
                })
        ).isInstanceOf(IllegalStateException.class);

        // Mesmo com exceção, o finally deve ter limpado o MDC — senão o
        // correlationId/clienteId da requisição com erro vazaria para a
        // próxima requisição atendida pela mesma thread do pool do servlet.
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("clienteId")).isNull();
    }
}
