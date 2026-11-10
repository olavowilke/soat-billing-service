package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.usecases.EstornarPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Registra os casos de uso, que nao conhecem o Spring. */
@Configuration
class OrcamentoUseCaseConfig {

    @Bean
    GerarOrcamentoUseCase gerarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas) {
        return new GerarOrcamentoUseCase(orcamentos, respostas);
    }

    @Bean
    EstornarPagamentoUseCase estornarPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway pagamentos,
            RespostaSagaGateway respostas, Clock clock) {
        return new EstornarPagamentoUseCase(orcamentos, pagamentos, respostas, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
