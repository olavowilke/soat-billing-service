package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.usecases.GerarOrcamentoCommand.ItemComando;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GerarOrcamentoUseCaseTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final String CORR = "corr-" + SAGA;
    private static final UUID OS = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final GerarOrcamentoUseCase useCase = new GerarOrcamentoUseCase(orcamentos, respostas);

    private static ItemComando cru(String valor) {
        return new ItemComando("SERVICO", UUID.randomUUID().toString(), "Troca de oleo", 1, valor);
    }

    private void esperaFalhou(ItemComando... itens) {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());

        useCase.execute(new GerarOrcamentoCommand(SAGA, CORR, OS, CLIENTE, List.of(itens)));

        verify(respostas).orcamentoFalhou(eq(SAGA), eq(CORR), eq(OS), anyString());
        verify(orcamentos, never()).salvar(any());
        verify(respostas, never()).orcamentoGerado(any(), any(), any(), any(), any());
    }

    @Test
    void geraOrcamentoEPublicaGerado() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(new GerarOrcamentoCommand(SAGA, CORR, OS, CLIENTE, List.of(cru("350.00"))));

        var salvo = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).salvar(salvo.capture());
        assertThat(salvo.getValue().getOsId()).isEqualTo(OS);
        assertThat(salvo.getValue().getSagaId()).isEqualTo(SAGA);
        verify(respostas).orcamentoGerado(SAGA, CORR, OS, salvo.getValue().getId(), new BigDecimal("350.00"));
    }

    @Test
    void itensVaziosPublicaFalhou() {
        esperaFalhou();
    }

    @Test
    void itensNulosPublicaFalhou() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());

        useCase.execute(new GerarOrcamentoCommand(SAGA, CORR, OS, CLIENTE, null));

        verify(respostas).orcamentoFalhou(eq(SAGA), eq(CORR), eq(OS), anyString());
    }

    @Test
    void totalZeradoPublicaFalhou() {
        esperaFalhou(cru("0.00"));
    }

    @Test
    void quantidadeZeroPublicaFalhou() {
        esperaFalhou(new ItemComando("SERVICO", UUID.randomUUID().toString(), "x", 0, "10.00"));
    }

    @Test
    void valorNegativoPublicaFalhou() {
        esperaFalhou(cru("-1.00"));
    }

    @Test
    void valorNaoNumericoPublicaFalhou() {
        esperaFalhou(cru("abc"));
    }

    @Test
    void valorAusentePublicaFalhou() {
        esperaFalhou(cru(null));
    }

    @Test
    void descricaoEmBrancoPublicaFalhou() {
        esperaFalhou(new ItemComando("SERVICO", UUID.randomUUID().toString(), " ", 1, "10.00"));
    }

    @Test
    void tipoInvalidoPublicaFalhou() {
        esperaFalhou(new ItemComando("OUTRO", UUID.randomUUID().toString(), "x", 1, "10.00"));
    }

    @Test
    void tipoAusentePublicaFalhou() {
        esperaFalhou(new ItemComando(null, UUID.randomUUID().toString(), "x", 1, "10.00"));
    }

    @Test
    void referenciaInvalidaPublicaFalhou() {
        esperaFalhou(new ItemComando("PECA", "nao-uuid", "x", 1, "10.00"));
    }

    @Test
    void osJaComOrcamentoReenviaGerado() {
        var existente = Orcamento.gerar(OS, SAGA, "corr-guardada", CLIENTE, List.of(new ItemOrcamento(TipoItem.SERVICO,
                UUID.randomUUID(), "x", 1, new BigDecimal("100.00"))), Instant.now());
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.of(existente));

        // O comando reenviado traz outro id de correlacao: o guardado no orcamento vence.
        useCase.execute(new GerarOrcamentoCommand(SAGA, "corr-do-reenvio", OS, CLIENTE, List.of(cru("999.00"))));

        verify(respostas).orcamentoGerado(SAGA, "corr-guardada", OS, existente.getId(), new BigDecimal("100.00"));
        verify(orcamentos, never()).salvar(any());
    }
}
