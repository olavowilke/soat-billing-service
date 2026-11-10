package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static br.com.oficina.billing.orcamento.usecases.OrcamentoTestes.AGORA;
import static br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento.APLICADO;
import static br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento.DESCONHECIDO;
import static br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento.ESTORNADO;
import static br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento.IGNORADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessarResultadoPagamentoUseCaseTest {

    private static final String MP_ID = "fake-123";

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final PagamentoGateway provedor = mock(PagamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final ProcessarResultadoPagamentoUseCase useCase = new ProcessarResultadoPagamentoUseCase(orcamentos,
            provedor, respostas, Clock.fixed(AGORA.plusSeconds(60), ZoneOffset.UTC));

    private final Logger logger = (Logger) LoggerFactory.getLogger(ProcessarResultadoPagamentoUseCase.class);
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

    private Orcamento aprovado() {
        Orcamento o = OrcamentoTestes.aprovado(UUID.randomUUID());
        when(orcamentos.buscarPorPagamentoId(pagId(o))).thenReturn(Optional.of(o));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        return o;
    }

    private UUID pagId(Orcamento o) {
        return o.getPagamento().orElseThrow().getId();
    }

    @Test
    void situacaoPendenteNaoFazNada() {
        Orcamento o = aprovado();

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.PENDENTE)).isEqualTo(IGNORADO);

        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(respostas, provedor);
    }

    @Test
    void aprovadoConfirmaEPublica() {
        Orcamento o = aprovado();

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.APROVADO)).isEqualTo(APLICADO);

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(o.getPagamento().orElseThrow().getMpPaymentId()).isEqualTo(MP_ID);
        verify(orcamentos).salvar(o);
        verify(respostas).pagamentoConfirmado(o.getSagaId(), o.getOsId(), o.getId(), pagId(o));
    }

    @Test
    void rejeitadoCancelaEPublicaFalhou() {
        Orcamento o = aprovado();

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.REJEITADO)).isEqualTo(APLICADO);

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(o.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.REJEITADO);
        verify(orcamentos).salvar(o);
        verify(respostas).pagamentoFalhou(o.getSagaId(), o.getOsId(), o.getId(), pagId(o), "REJEITADO");
    }

    @Test
    void webhookDuplicadoAprovadoNaoPublicaDeNovo() {
        Orcamento o = aprovado();
        useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.APROVADO);

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.APROVADO)).isEqualTo(IGNORADO);

        verify(orcamentos).salvar(o);
        verify(respostas).pagamentoConfirmado(any(), any(), any(), any());
    }

    @Test
    void webhookDuplicadoRejeitadoNaoPublicaDeNovo() {
        Orcamento o = aprovado();
        useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.REJEITADO);

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.REJEITADO)).isEqualTo(IGNORADO);

        verify(respostas).pagamentoFalhou(any(), any(), any(), any(), any());
    }

    @Test
    void aprovadoDepoisDeExpiradoEstornaSemResponder() {
        Orcamento o = aprovado();
        o.expirarPagamento(AGORA.plusSeconds(30));

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.APROVADO)).isEqualTo(ESTORNADO);

        verify(provedor).estornar(MP_ID, pagId(o));
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(respostas);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains(pagId(o).toString());
        });
    }

    @Test
    void rejeitadoDepoisDeExpiradoNaoFazNada() {
        Orcamento o = aprovado();
        o.expirarPagamento(AGORA.plusSeconds(30));

        assertThat(useCase.execute(pagId(o), MP_ID, SituacaoPagamentoExterno.REJEITADO)).isEqualTo(IGNORADO);

        verifyNoInteractions(provedor, respostas);
    }

    @Test
    void pagamentoDesconhecidoLogaWarnESegue() {
        UUID pagamentoId = UUID.randomUUID();
        when(orcamentos.buscarPorPagamentoId(pagamentoId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(pagamentoId, MP_ID, SituacaoPagamentoExterno.APROVADO)).isEqualTo(DESCONHECIDO);

        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(respostas, provedor);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains(pagamentoId.toString());
        });
    }

    @Test
    void mpPaymentIdEmBrancoNoAprovadoLancaIllegalArgument() {
        Orcamento o = aprovado();

        assertThatThrownBy(() -> useCase.execute(pagId(o), " ", SituacaoPagamentoExterno.APROVADO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.execute(pagId(o), null, SituacaoPagamentoExterno.APROVADO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        verifyNoInteractions(respostas);
    }
}
