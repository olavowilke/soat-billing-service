package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.TransicaoInvalidaException;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static br.com.oficina.billing.orcamento.usecases.OrcamentoTestes.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecusarOrcamentoUseCaseTest {

    private static final UUID CLIENTE = UUID.randomUUID();

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final RecusarOrcamentoUseCase useCase = new RecusarOrcamentoUseCase(orcamentos, respostas,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    private Orcamento existente(Orcamento o) {
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        return o;
    }

    @Test
    void recusaSalvaEPublicaRecusado() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));

        Orcamento resultado = useCase.execute(o.getId(), Optional.of(CLIENTE));

        assertThat(resultado.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        verify(orcamentos).salvar(o);
        verify(respostas).orcamentoRecusado(o.getSagaId(), o.getOsId(), o.getId());
    }

    @Test
    void outroClienteLancaNaoAutorizado() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));

        assertThatThrownBy(() -> useCase.execute(o.getId(), Optional.of(UUID.randomUUID())))
                .isInstanceOf(ClienteNaoAutorizadoException.class);
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(respostas);
    }

    @Test
    void desconhecidoLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(orcamentos.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, Optional.empty()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void jaAprovadoLancaTransicaoInvalida() {
        Orcamento o = existente(OrcamentoTestes.aprovado(CLIENTE));

        assertThatThrownBy(() -> useCase.execute(o.getId(), Optional.empty()))
                .isInstanceOf(TransicaoInvalidaException.class);
        verifyNoInteractions(respostas);
    }
}
