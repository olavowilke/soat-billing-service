package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.gateways.ExecutorTransacional;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.oficina.billing.orcamento.usecases.OrcamentoTestes.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpirarPagamentosUseCaseTest {

    private static final Instant DEPOIS = AGORA.plusSeconds(3600);

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    /** Faz de conta que cada linha roda em transacao propria e confirma. */
    private final ExecutorTransacional confirma = acao -> {
        acao.run();
        return true;
    };

    private final Logger logger = (Logger) LoggerFactory.getLogger(ExpirarPagamentosUseCase.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.setAdditive(false);
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
        logger.setAdditive(true);
    }

    private ExpirarPagamentosUseCase useCase(ExecutorTransacional tx) {
        return new ExpirarPagamentosUseCase(orcamentos, respostas, tx);
    }

    private Orcamento vencido() {
        Orcamento o = OrcamentoTestes.aprovado(UUID.randomUUID());
        when(orcamentos.buscarPorId(o.getId())).thenReturn(java.util.Optional.of(o));
        return o;
    }

    private static UUID pagId(Orcamento o) {
        return o.getPagamento().orElseThrow().getId();
    }

    @Test
    void expiraVencidosEPublicaFalhouExpirado() {
        Orcamento a = vencido();
        Orcamento b = vencido();
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of(a, b));

        int expirados = useCase(confirma).execute(DEPOIS);

        assertThat(expirados).isEqualTo(2);
        for (Orcamento o : List.of(a, b)) {
            assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
            assertThat(o.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
            verify(orcamentos).salvar(o);
            verify(respostas).pagamentoFalhou(o.getSagaId(), o.getCorrelationId(), o.getOsId(), o.getId(), pagId(o),
                    "EXPIRADO");
        }
    }

    @Test
    void semVencidosNaoFazNada() {
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of());

        assertThat(useCase(confirma).execute(DEPOIS)).isZero();

        verify(orcamentos, never()).salvar(any());
        verify(respostas, never()).pagamentoFalhou(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void conflitoOtimistaEmUmaLinhaPulaEssaLinhaEContinua() {
        Orcamento a = vencido();
        Orcamento b = vencido();
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of(a, b));
        var chamada = new AtomicInteger();
        // A primeira linha perde a corrida (false = transacao desfeita por lock otimista).
        ExecutorTransacional primeiraPerde = acao -> {
            acao.run();
            return chamada.incrementAndGet() != 1;
        };

        int expirados = useCase(primeiraPerde).execute(DEPOIS);

        assertThat(expirados).isEqualTo(1);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains(a.getId().toString());
        });
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    @Test
    void linhaQueJaNaoEstaPendenteNaLeituraDentroDaTransacaoEhIgnorada() {
        Orcamento paga = vencido();
        paga.confirmarPagamento("mp-1", AGORA);
        Orcamento ok = vencido();
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of(paga, ok));

        int expirados = useCase(confirma).execute(DEPOIS);

        assertThat(expirados).isEqualTo(1);
        assertThat(paga.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        verify(respostas, never()).pagamentoFalhou(eq(paga.getSagaId()), any(), any(), any(), any(), anyString());
    }

    @Test
    void linhaQueSumiuNaLeituraDentroDaTransacaoEhIgnorada() {
        Orcamento fantasma = OrcamentoTestes.aprovado(UUID.randomUUID());
        when(orcamentos.buscarPorId(fantasma.getId())).thenReturn(java.util.Optional.empty());
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of(fantasma));

        assertThat(useCase(confirma).execute(DEPOIS)).isZero();
    }

    @Test
    void falhaInesperadaEmUmaLinhaLogaErroEContinua() {
        Orcamento a = vencido();
        Orcamento b = vencido();
        when(orcamentos.buscarComPagamentoVencido(DEPOIS, 50)).thenReturn(List.of(a, b));
        var chamada = new AtomicInteger();
        ExecutorTransacional primeiraQuebra = acao -> {
            if (chamada.incrementAndGet() == 1) {
                throw new IllegalStateException("banco caiu");
            }
            acao.run();
            return true;
        };

        int expirados = useCase(primeiraQuebra).execute(DEPOIS);

        assertThat(expirados).isEqualTo(1);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains(a.getId().toString());
        });
    }
}
