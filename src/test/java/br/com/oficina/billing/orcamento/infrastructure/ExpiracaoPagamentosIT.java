package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.usecases.ExpirarPagamentosUseCase;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento;
import br.com.oficina.billing.shared.BillingIT;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Expiracao do pagamento e a corrida com o resultado do provedor. O job nao roda sozinho nos testes
 * (billing.pagamento.verificacao muito longo): os testes chamam o caso de uso e o job direto.
 */
class ExpiracaoPagamentosIT extends BillingIT {

    @Autowired
    private ExpirarPagamentosUseCase expirar;
    @Autowired
    private ProcessarResultadoPagamentoUseCase processar;
    @Autowired
    private ExpiracaoPagamentosJob job;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;

    private final Logger logger = (Logger) LoggerFactory.getLogger(ExpirarPagamentosUseCase.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger loggerProcessar = (Logger) LoggerFactory.getLogger(ProcessarResultadoPagamentoUseCase.class);
    private final ListAppender<ILoggingEvent> logsProcessar = new ListAppender<>();

    @BeforeEach
    void preparar() {
        jdbc.update("delete from outbox");
        logs.start();
        logger.setAdditive(false);
        logger.addAppender(logs);
        logsProcessar.start();
        loggerProcessar.setAdditive(false);
        loggerProcessar.addAppender(logsProcessar);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(logs);
        logger.setAdditive(true);
        loggerProcessar.detachAppender(logsProcessar);
        loggerProcessar.setAdditive(true);
        jdbc.update("delete from outbox");
    }

    /** Aprovado ha 2 horas, com prazo de 30 minutos: o pagamento venceu ha 90 minutos. */
    private Orcamento vencido(Duration haQuantoTempoVenceu) {
        Instant criado = Instant.now().minus(haQuantoTempoVenceu).minus(Duration.ofMinutes(30));
        Orcamento o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), "corr-" + UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Servico", 1,
                        new BigDecimal("100.00"))), criado);
        o.aprovar(criado, Duration.ofMinutes(30)).definirCobranca("pref", "https://pagar");
        return orcamentoGateway.salvar(o);
    }

    private Orcamento vencido() {
        return vencido(Duration.ofMinutes(90));
    }

    private static UUID pagId(Orcamento o) {
        return o.getPagamento().orElseThrow().getId();
    }

    private Orcamento recarregar(Orcamento o) {
        return orcamentoGateway.buscarPorId(o.getId()).orElseThrow();
    }

    private List<String> tiposNaOutbox(Orcamento o) {
        return jdbc.queryForList("select tipo from outbox where saga_id = ? order by criado_em", String.class,
                o.getSagaId());
    }

    @Test
    void expiraPagamentoVencidoEGravaFalhouComOIdDaSaga() {
        Orcamento o = vencido();

        int expirados = expirar.execute(Instant.now());

        assertThat(expirados).isGreaterThanOrEqualTo(1);
        Orcamento depois = recarregar(o);
        assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(depois.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(jdbc.queryForList("select payload::text from outbox where saga_id = ? and tipo = 'PagamentoFalhou'",
                String.class, o.getSagaId())).singleElement().asString().contains("EXPIRADO");
        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class,
                o.getSagaId())).isEqualTo(o.getCorrelationId());
    }

    @Test
    void naoExpiraPagamentoAindaDentroDoPrazo() {
        Instant criado = Instant.now();
        Orcamento o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), "corr-x", UUID.randomUUID(),
                List.of(new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Servico", 1, BigDecimal.TEN)),
                criado);
        o.aprovar(criado, Duration.ofMinutes(30)).definirCobranca("pref", "https://pagar");
        o = orcamentoGateway.salvar(o);

        expirar.execute(Instant.now());

        assertThat(recarregar(o).getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(tiposNaOutbox(o)).isEmpty();
    }

    @Test
    void jobExpiraOsVencidos() {
        Orcamento o = vencido();

        job.verificar();

        assertThat(recarregar(o).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(tiposNaOutbox(o)).containsExactly("PagamentoFalhou");
    }

    @Test
    void jobUsaOAtrasoConfiguradoEDeveSerLongoNosTestes() throws Exception {
        Scheduled agendado = ExpiracaoPagamentosJob.class.getMethod("verificar").getAnnotation(Scheduled.class);

        assertThat(agendado.fixedDelayString()).isEqualTo("${billing.pagamento.verificacao:PT30S}");
        assertThat(agendado.initialDelayString()).isEqualTo("${billing.pagamento.verificacao:PT30S}");
    }

    @Test
    void conflitoOtimistaEmUmaLinhaPulaEssaLinhaEExpiraAsOutras() {
        Orcamento primeira = vencido(Duration.ofMinutes(120));
        Orcamento segunda = vencido(Duration.ofMinutes(100));
        // O gateway de orcamento e um spy: a primeira gravacao perde a corrida, as seguintes funcionam.
        doThrow(new ObjectOptimisticLockingFailureException(Orcamento.class, primeira.getId()))
                .doCallRealMethod().when(orcamentoGateway).salvar(any());

        expirar.execute(Instant.now());

        assertThat(recarregar(primeira).getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(tiposNaOutbox(primeira)).isEmpty();
        assertThat(recarregar(segunda).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(tiposNaOutbox(segunda)).containsExactly("PagamentoFalhou");
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains(primeira.getId().toString());
        });
        // A linha pulada fica para a proxima rodada.
        doCallRealMethod().when(orcamentoGateway).salvar(any());
        expirar.execute(Instant.now());
        assertThat(recarregar(primeira).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
    }

    @Test
    void aprovadoDepoisDeExpiradoEstornaEMantemSoARespostaDeFalhou() {
        Orcamento o = vencido();
        expirar.execute(Instant.now());

        ResultadoProcessamento r = tx.execute(
                s -> processar.execute(pagId(o), "mp-tardio", SituacaoPagamentoExterno.APROVADO));

        assertThat(r).isEqualTo(ResultadoProcessamento.ESTORNADO);
        verify(pagamentoGateway).estornar("mp-tardio", pagId(o));
        assertThat(logsProcessar.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains("EXPIRADO").contains("mp-tardio");
        });
        assertThat(tiposNaOutbox(o)).containsExactly("PagamentoFalhou");
        assertThat(recarregar(o).getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
    }

    @Test
    void pagoAntesDaExpiracaoNaoExpira() {
        Orcamento o = vencido();
        tx.executeWithoutResult(s -> processar.execute(pagId(o), "mp-ok", SituacaoPagamentoExterno.APROVADO));

        expirar.execute(Instant.now());

        assertThat(recarregar(o).getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(tiposNaOutbox(o)).containsExactly("PagamentoConfirmado");
        verify(pagamentoGateway, never()).estornar(any(), any());
    }

    @Test
    void corridaWebhookExpiracaoTemUmSoResultado() throws Exception {
        int rodadas = 15;
        int confirmou = 0;
        int expirou = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < rodadas; i++) {
                Orcamento o = vencido();
                CountDownLatch largada = new CountDownLatch(1);
                Future<ResultadoProcessamento> webhook = pool.submit(() -> {
                    largada.await();
                    try {
                        return tx.execute(s -> processar.execute(pagId(o), "mp-corrida", SituacaoPagamentoExterno.APROVADO));
                    } catch (ObjectOptimisticLockingFailureException perdeu) {
                        // A expiracao gravou primeiro. O Mercado Pago repete o webhook e o estorno acontece nessa vez.
                        return null;
                    }
                });
                Future<Integer> expiracao = pool.submit(() -> {
                    largada.await();
                    return expirar.execute(Instant.now());
                });
                largada.countDown();
                webhook.get(30, TimeUnit.SECONDS);
                expiracao.get(30, TimeUnit.SECONDS);

                List<String> tipos = tiposNaOutbox(o);
                assertThat(tipos).as("rodada %d", i).hasSize(1);
                Orcamento depois = recarregar(o);
                if (tipos.get(0).equals("PagamentoConfirmado")) {
                    confirmou++;
                    assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.PAGO);
                } else {
                    expirou++;
                    assertThat(tipos).containsExactly("PagamentoFalhou");
                    assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(confirmou + expirou).isEqualTo(rodadas);
        // Toda linha pulada por conflito foi avisada com WARN; nenhum erro inesperado.
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }
}
