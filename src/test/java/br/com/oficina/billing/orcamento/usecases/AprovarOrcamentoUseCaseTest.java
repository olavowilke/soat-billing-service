package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.entities.TransicaoInvalidaException;
import br.com.oficina.billing.orcamento.gateways.CobrancaCriada;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.ProvedorPagamentoIndisponivelException;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static br.com.oficina.billing.orcamento.usecases.OrcamentoTestes.AGORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AprovarOrcamentoUseCaseTest {

    private static final UUID CLIENTE = UUID.randomUUID();

    private final OrcamentoGateway orcamentos = mock(OrcamentoGateway.class);
    private final PagamentoGateway provedor = mock(PagamentoGateway.class);
    private final RespostaSagaGateway respostas = mock(RespostaSagaGateway.class);
    private final AprovarOrcamentoUseCase useCase = new AprovarOrcamentoUseCase(orcamentos, provedor, respostas,
            Clock.fixed(AGORA, ZoneOffset.UTC), Duration.ofMinutes(30));

    private Orcamento existente(Orcamento o) {
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        return o;
    }

    @Test
    void aprovaCriaCobrancaSalvaEPublicaAprovado() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));
        when(provedor.criarCobranca(any(), anyString(), eq(new BigDecimal("100.00"))))
                .thenReturn(new CobrancaCriada("pref-1", "https://pagar/1"));

        Orcamento resultado = useCase.execute(o.getId(), Optional.of(CLIENTE));

        var pagamento = resultado.getPagamento().orElseThrow();
        assertThat(resultado.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getLinkPagamento()).isEqualTo("https://pagar/1");
        assertThat(pagamento.getMpPreferenceId()).isEqualTo("pref-1");
        assertThat(pagamento.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
        verify(provedor).criarCobranca(eq(pagamento.getId()), anyString(), eq(new BigDecimal("100.00")));
        verify(orcamentos).salvar(o);
        verify(respostas).orcamentoAprovado(o.getSagaId(), o.getOsId(), o.getId(), pagamento.getId(),
                "https://pagar/1");
    }

    @Test
    void semCabecalhoPermite() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));
        when(provedor.criarCobranca(any(), anyString(), any())).thenReturn(new CobrancaCriada("p", "l"));

        assertThat(useCase.execute(o.getId(), Optional.empty()).getStatus()).isEqualTo(StatusOrcamento.APROVADO);
    }

    @Test
    void outroClienteLancaNaoAutorizadoSemEfeito() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));

        assertThatThrownBy(() -> useCase.execute(o.getId(), Optional.of(UUID.randomUUID())))
                .isInstanceOf(ClienteNaoAutorizadoException.class);
        verifyNoInteractions(provedor, respostas);
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.GERADO);
    }

    @Test
    void orcamentoDesconhecidoLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(orcamentos.buscarPorId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, Optional.empty()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void jaAprovadoLancaTransicaoInvalidaSemChamarProvedor() {
        Orcamento o = existente(OrcamentoTestes.aprovado(CLIENTE));

        assertThatThrownBy(() -> useCase.execute(o.getId(), Optional.of(CLIENTE)))
                .isInstanceOf(TransicaoInvalidaException.class);
        verifyNoInteractions(provedor, respostas);
    }

    @Test
    void falhaDoProvedorPropagaSemSalvarNemPublicar() {
        Orcamento o = existente(OrcamentoTestes.gerado(CLIENTE));
        when(provedor.criarCobranca(any(), anyString(), any()))
                .thenThrow(new ProvedorPagamentoIndisponivelException("fora do ar"));

        assertThatThrownBy(() -> useCase.execute(o.getId(), Optional.empty()))
                .isInstanceOf(ProvedorPagamentoIndisponivelException.class);
        verify(orcamentos, never()).salvar(any());
        verifyNoInteractions(respostas);
    }
}
