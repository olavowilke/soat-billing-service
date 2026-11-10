package br.com.oficina.billing.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlaciona os logs de uma requisição com o {@code requestId} gerado pelo
 * API Gateway (ver {@code lambda-auth/terraform/apigateway.tf}), colocando-o
 * no MDC do SLF4J sob a chave {@code correlationId} para que todas as linhas
 * de log emitidas durante o processamento da requisição carreguem esse valor.
 *
 * <p>O Gateway injeta o cabeçalho {@code X-Correlation-Id} com
 * {@code $context.requestId} para toda chamada encaminhada ao cluster. Quando
 * o cabeçalho não vier (ex.: chamada direta ao serviço, fora do Gateway),
 * geramos um UUID novo para não perder a rastreabilidade.
 *
 * <p><b>Importante sobre {@code X-Cliente-Id}:</b> o Gateway também envia
 * {@code X-Cliente-Id} (a partir de {@code $context.authorizer.clienteId}),
 * mas esse valor é usado <b>apenas para logging</b> — nunca para autorização.
 * O {@code clienteId} autoritativo vem da claim assinada do JWT, verificada em
 * {@code JwtAuthenticationFilter}. Um cabeçalho HTTP pode ser forjado por
 * qualquer chamada que alcance o load balancer a partir de dentro da VPC; uma
 * assinatura JWT, não. Colocar esse valor no contexto de segurança abriria uma
 * forma de um cliente se passar por outro.
 *
 * <p>É registrado com {@link Order @Order(Ordered.HIGHEST_PRECEDENCE)} para
 * que o correlationId já esteja disponível no MDC antes de qualquer outro
 * filtro (incluindo o {@code JwtAuthenticationFilter}) executar — assim, até
 * mesmo falhas de autenticação são logadas com correlação.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CORRELATION_ID = "X-Correlation-Id";
    public static final String CABECALHO_CLIENTE_ID = "X-Cliente-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_CLIENTE_ID = "clienteId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CABECALHO_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String clienteId = request.getHeader(CABECALHO_CLIENTE_ID);

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            if (clienteId != null && !clienteId.isBlank()) {
                MDC.put(MDC_CLIENTE_ID, clienteId);
            }
            response.setHeader(CABECALHO_CORRELATION_ID, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            // Threads do pool do servlet são reaproveitadas entre requisições:
            // se não limparmos aqui, o correlationId (e o clienteId) desta
            // requisição vazaria para a próxima que a mesma thread atender —
            // inclusive quando a cadeia de filtros lança exceção.
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_CLIENTE_ID);
        }
    }
}
