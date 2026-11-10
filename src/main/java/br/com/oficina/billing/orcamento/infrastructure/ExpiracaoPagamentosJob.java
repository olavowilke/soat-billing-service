package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.usecases.ExpirarPagamentosUseCase;
import com.newrelic.api.agent.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Procura pagamentos vencidos a cada billing.pagamento.verificacao (padrao 30 segundos). */
@Component
public class ExpiracaoPagamentosJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoPagamentosJob.class);

    private final ExpirarPagamentosUseCase expirar;
    private final Clock clock;

    public ExpiracaoPagamentosJob(ExpirarPagamentosUseCase expirar, Clock clock) {
        this.expirar = expirar;
        this.clock = clock;
    }

    @Trace(dispatcher = true)
    @Scheduled(fixedDelayString = "${billing.pagamento.verificacao:PT30S}",
            initialDelayString = "${billing.pagamento.verificacao:PT30S}")
    public void verificar() {
        try {
            int expirados = expirar.execute(clock.instant());
            if (expirados > 0) {
                log.info("Pagamentos expirados nesta rodada: {}", expirados);
            }
        } catch (RuntimeException e) {
            // Uma falha (por exemplo, banco fora) nao pode matar o agendador: a proxima rodada tenta de novo.
            log.error("Falha ao verificar pagamentos vencidos", e);
        }
    }
}
