package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra os casos de uso, que nao conhecem o Spring. */
@Configuration
class OrcamentoUseCaseConfig {

    @Bean
    GerarOrcamentoUseCase gerarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas) {
        return new GerarOrcamentoUseCase(orcamentos, respostas);
    }
}
