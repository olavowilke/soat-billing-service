package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstornarPagamentoUseCaseTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final UUID OS = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final PagamentoGateway pagamentos = mock(PagamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final EstornarPagamentoUseCase useCase = new EstornarPagamentoUseCase(orcamentos, pagamentos, respostas,
            Clock.fixed(AGORA, ZoneOffset.UTC), 3);

    private final Logger logger = (Logger) LoggerFactory.getLogger(EstornarPagamentoUseCase.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capturarLogs() {
        logs.start();
        logger.addAppender(logs);
        logger.setAdditive(false);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
        logger.setAdditive(true);
    }

    private static Orcamento pago(String mpPaymentId) {
        var o = Orcamento.gerar(OS, SAGA, UUID.randomUUID(), List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1, new BigDecimal("350.00"))),
                AGORA.minusSeconds(60));
        o.aprovar(AGORA.minusSeconds(50), Duration.ofMinutes(30));
        o.confirmarPagamento(mpPaymentId, AGORA.minusSeconds(40));
        return o;
    }

    private void dado(Orcamento o) {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.of(o));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void estornaPagamentoPago() {
        var o = pago("mp-1");
        dado(o);

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.ESTORNADO);
        var pagamentoId = o.getPagamento().orElseThrow().getId();
        verify(pagamentos).estornar("mp-1", pagamentoId);
        verify(orcamentos).salvar(o);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(o.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(o.getAtualizadoEm()).isEqualTo(AGORA);
        verify(respostas).pagamentoEstornado(SAGA, OS, pagamentoId);
    }

    @Test
    void estornoRepetidoReenviaEstornado() {
        var o = pago("mp-1");
        o.estornar(AGORA.minusSeconds(10));
        dado(o);

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.ESTORNADO);
        verify(pagamentos, never()).estornar(anyString(), any());
        verify(orcamentos, never()).salvar(any());
        verify(respostas).pagamentoEstornado(SAGA, OS, o.getPagamento().orElseThrow().getId());
    }

    @Test
    void falhaNaPrimeiraTentativaPedeNovaTentativa() {
        var o = pago("mp-1");
        dado(o);
        doThrow(new IllegalStateException("provedor fora")).when(pagamentos).estornar(anyString(), any());

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.TENTAR_DE_NOVO);
        assertThat(o.getPagamento().orElseThrow().getTentativasEstorno()).isEqualTo(1);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        verify(orcamentos).salvar(o);
        verify(respostas, never()).estornoFalhou(any(), any(), any(), anyString());
        verify(respostas, never()).pagamentoEstornado(any(), any(), any());
    }

    @Test
    void terceiraFalhaDesisteEPublicaEstornoFalhou() {
        var o = pago("mp-1");
        dado(o);
        doThrow(new IllegalStateException("provedor fora")).when(pagamentos).estornar(anyString(), any());

        assertThat(useCase.execute(SAGA, OS, 1)).isEqualTo(ResultadoEstorno.TENTAR_DE_NOVO);
        assertThat(useCase.execute(SAGA, OS, 2)).isEqualTo(ResultadoEstorno.TENTAR_DE_NOVO);
        var r = useCase.execute(SAGA, OS, 3);

        assertThat(r).isEqualTo(ResultadoEstorno.DESISTIU);
        assertThat(o.getPagamento().orElseThrow().getTentativasEstorno()).isEqualTo(3);
        assertThat(logs.list).anyMatch(e -> e.getLevel() == Level.ERROR);
        verify(respostas).estornoFalhou(eq(SAGA), eq(OS), eq(o.getPagamento().orElseThrow().getId()),
                anyString());
    }

    @Test
    void pagamentoSemIdentificadorNoProvedorDesisteNaHora() {
        var o = pago(" ");
        dado(o);

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.DESISTIU);
        verify(pagamentos, never()).estornar(anyString(), any());
        verify(respostas).estornoFalhou(SAGA, OS, o.getPagamento().orElseThrow().getId(),
                "pagamento sem identificador no provedor");
    }

    @Test
    void orcamentoAindaAprovadoDesisteComMotivo() {
        var o = Orcamento.gerar(OS, SAGA, UUID.randomUUID(), List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1, new BigDecimal("10.00"))),
                AGORA);
        var pagamento = o.aprovar(AGORA, Duration.ofMinutes(30));
        dado(o);

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.DESISTIU);
        verify(pagamentos, never()).estornar(anyString(), any());
        verify(respostas).estornoFalhou(eq(SAGA), eq(OS), eq(pagamento.getId()), anyString());
    }

    @Test
    void osSemOrcamentoDesisteComMotivo() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());

        var r = useCase.execute(SAGA, OS, 1);

        assertThat(r).isEqualTo(ResultadoEstorno.DESISTIU);
        verify(respostas).estornoFalhou(eq(SAGA), eq(OS), eq(null), anyString());
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void ultimaEntregaDesisteMesmoSemFalhasContadasAntes() {
        var o = pago("mp-1");
        dado(o);
        doThrow(new IllegalStateException("provedor fora")).when(pagamentos).estornar(anyString(), any());

        var r = useCase.execute(SAGA, OS, 3);

        assertThat(r).isEqualTo(ResultadoEstorno.DESISTIU);
        assertThat(o.getPagamento().orElseThrow().getTentativasEstorno()).isEqualTo(1);
        verify(orcamentos).salvar(o);
        verify(respostas).estornoFalhou(eq(SAGA), eq(OS), eq(o.getPagamento().orElseThrow().getId()),
                anyString());
    }

    @Test
    void entregaIntermediariaComUmaFalhaContadaPedeNovaTentativa() {
        var o = pago("mp-1");
        dado(o);
        doThrow(new IllegalStateException("provedor fora")).when(pagamentos).estornar(anyString(), any());

        assertThat(useCase.execute(SAGA, OS, 2)).isEqualTo(ResultadoEstorno.TENTAR_DE_NOVO);

        verify(respostas, never()).estornoFalhou(any(), any(), any(), anyString());
    }

    @Test
    void ultimaEntregaComProvedorOkEstorna() {
        dado(pago("mp-1"));

        assertThat(useCase.execute(SAGA, OS, 3)).isEqualTo(ResultadoEstorno.ESTORNADO);
    }
}
