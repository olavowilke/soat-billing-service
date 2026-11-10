package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuscarOrcamentoUseCaseTest {

    private static final UUID CLIENTE = UUID.randomUUID();

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final BuscarOrcamentoUseCase useCase = new BuscarOrcamentoUseCase(orcamentos);

    @Test
    void donoOuEquipeRecebemOOrcamento() {
        Orcamento o = OrcamentoTestes.gerado(CLIENTE);
        when(orcamentos.buscarPorOsId(o.getOsId())).thenReturn(Optional.of(o));

        assertThat(useCase.execute(o.getOsId(), Optional.of(CLIENTE))).isSameAs(o);
        assertThat(useCase.execute(o.getOsId(), Optional.empty())).isSameAs(o);
    }

    @Test
    void outroClienteLancaNaoAutorizado() {
        Orcamento o = OrcamentoTestes.gerado(CLIENTE);
        when(orcamentos.buscarPorOsId(o.getOsId())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> useCase.execute(o.getOsId(), Optional.of(UUID.randomUUID())))
                .isInstanceOf(ClienteNaoAutorizadoException.class);
    }

    @Test
    void osSemOrcamentoLancaNaoEncontrado() {
        UUID osId = UUID.randomUUID();
        when(orcamentos.buscarPorOsId(osId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(osId, Optional.empty()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
