package br.com.oficina.billing.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registra uma linha de entrada e uma de saída por requisição, com o método,
 * o caminho, o status da resposta e a duração.
 *
 * <p>Roda logo depois do {@link CorrelationIdFilter}, então as duas linhas já
 * saem com o {@code correlationId} no MDC — é isso que permite juntar a
 * requisição inteira num filtro só no New Relic.
 *
 * <p>As rotas do Actuator ficam de fora: o kubelet chama as probes a cada
 * poucos segundos e o monitor sintético a cada 5 minutos. Logar isso afogaria
 * os logs de negócio e viraria custo de ingestão sem valor nenhum.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String metodo = request.getMethod();
        String caminho = request.getRequestURI();

        if (caminho.contains("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("Requisicao recebida: {} {}", metodo, caminho);
        long inicio = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("Requisicao concluida: {} {} -> {} ({} ms)",
                    metodo, caminho, response.getStatus(), duracaoMs);
        }
    }
}
