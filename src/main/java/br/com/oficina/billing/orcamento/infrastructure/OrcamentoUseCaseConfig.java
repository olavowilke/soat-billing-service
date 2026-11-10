package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.EstornoAvulsoGateway;
import br.com.oficina.billing.orcamento.gateways.ExecutorTransacional;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.usecases.AprovarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.BuscarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.BuscarPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.EstornarPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ExpirarPagamentosUseCase;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.RecusarOrcamentoUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Registra os casos de uso, que nao conhecem o Spring. */
@Configuration
class OrcamentoUseCaseConfig {

    @Bean
    GerarOrcamentoUseCase gerarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas) {
        return new GerarOrcamentoUseCase(orcamentos, respostas);
    }

    @Bean
    EstornarPagamentoUseCase estornarPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway pagamentos,
            RespostaSagaGateway respostas, Clock clock,
            @Value("${billing.estorno.max-entregas:3}") int maxEntregas) {
        return new EstornarPagamentoUseCase(orcamentos, pagamentos, respostas, clock, maxEntregas);
    }

    @Bean
    AprovarOrcamentoUseCase aprovarOrcamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway pagamentos,
            RespostaSagaGateway respostas, Clock clock,
            @Value("${billing.pagamento.prazo:PT30M}") String prazo) {
        return new AprovarOrcamentoUseCase(orcamentos, pagamentos, respostas, clock, Duration.parse(prazo));
    }

    @Bean
    RecusarOrcamentoUseCase recusarOrcamentoUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas,
            Clock clock) {
        return new RecusarOrcamentoUseCase(orcamentos, respostas, clock);
    }

    @Bean
    BuscarOrcamentoUseCase buscarOrcamentoUseCase(OrcamentoGateway orcamentos) {
        return new BuscarOrcamentoUseCase(orcamentos);
    }

    @Bean
    BuscarPagamentoUseCase buscarPagamentoUseCase(OrcamentoGateway orcamentos) {
        return new BuscarPagamentoUseCase(orcamentos);
    }

    @Bean
    ProcessarResultadoPagamentoUseCase processarResultadoPagamentoUseCase(OrcamentoGateway orcamentos,
            PagamentoGateway pagamentos, RespostaSagaGateway respostas, EstornoAvulsoGateway avulsos, Clock clock) {
        return new ProcessarResultadoPagamentoUseCase(orcamentos, pagamentos, respostas, avulsos, clock);
    }

    @Bean
    ExpirarPagamentosUseCase expirarPagamentosUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas,
            ExecutorTransacional transacao) {
        return new ExpirarPagamentosUseCase(orcamentos, respostas, transacao);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
