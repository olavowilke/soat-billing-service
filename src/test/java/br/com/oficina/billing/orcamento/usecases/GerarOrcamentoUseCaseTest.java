package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
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
    private static final UUID OS = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final GerarOrcamentoUseCase useCase = new GerarOrcamentoUseCase(orcamentos, respostas);

    private static ItemOrcamento item(String valor) {
        return new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1, new BigDecimal(valor));
    }

    @Test
    void geraOrcamentoEPublicaGerado() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(new GerarOrcamentoCommand(SAGA, OS, CLIENTE, List.of(item("350.00"))));

        var salvo = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).salvar(salvo.capture());
        assertThat(salvo.getValue().getOsId()).isEqualTo(OS);
        assertThat(salvo.getValue().getSagaId()).isEqualTo(SAGA);
        verify(respostas).orcamentoGerado(SAGA, OS, salvo.getValue().getId(), new BigDecimal("350.00"));
    }

    @Test
    void itensVaziosPublicaFalhou() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());

        useCase.execute(new GerarOrcamentoCommand(SAGA, OS, CLIENTE, List.of()));

        verify(respostas).orcamentoFalhou(eq(SAGA), eq(OS), anyString());
        verify(orcamentos, never()).salvar(any());
        verify(respostas, never()).orcamentoGerado(any(), any(), any(), any());
    }

    @Test
    void totalZeradoPublicaFalhou() {
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.empty());

        useCase.execute(new GerarOrcamentoCommand(SAGA, OS, CLIENTE, List.of(item("0.00"))));

        verify(respostas).orcamentoFalhou(eq(SAGA), eq(OS), anyString());
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void osJaComOrcamentoReenviaGerado() {
        var existente = Orcamento.gerar(OS, SAGA, CLIENTE, List.of(item("100.00")), Instant.now());
        when(orcamentos.buscarPorOsId(OS)).thenReturn(Optional.of(existente));

        useCase.execute(new GerarOrcamentoCommand(SAGA, OS, CLIENTE, List.of(item("999.00"))));

        verify(respostas).orcamentoGerado(SAGA, OS, existente.getId(), new BigDecimal("100.00"));
        verify(orcamentos, never()).salvar(any());
    }
}
