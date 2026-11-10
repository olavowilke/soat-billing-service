package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoGatewayImplIT extends BillingIT {

    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private OrcamentoGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc.update("delete from pagamentos");
        jdbc.update("delete from itens_orcamento");
        jdbc.update("delete from orcamentos");
    }

    private static List<ItemOrcamento> itens() {
        return List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1, new BigDecimal("150.00")),
                new ItemOrcamento(TipoItem.PECA, UUID.randomUUID(), "Filtro", 2, new BigDecimal("100.00")));
    }

    private static Orcamento novo(UUID osId) {
        return Orcamento.gerar(osId, UUID.randomUUID(), UUID.randomUUID(), itens(), AGORA);
    }

    @Test
    void salvaERecuperaComItensEPagamento() {
        var orcamento = novo(UUID.randomUUID());
        var salvo = gateway.salvar(orcamento);

        assertThat(salvo.getVersao()).isZero();
        var lido = gateway.buscarPorId(orcamento.getId()).orElseThrow();
        assertThat(lido.getOsId()).isEqualTo(orcamento.getOsId());
        assertThat(lido.getSagaId()).isEqualTo(orcamento.getSagaId());
        assertThat(lido.getClienteId()).isEqualTo(orcamento.getClienteId());
        assertThat(lido.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(lido.getValorTotal()).isEqualByComparingTo("350.00");
        assertThat(lido.getItens()).containsExactlyElementsOf(orcamento.getItens());
        assertThat(lido.getPagamento()).isEmpty();
        assertThat(lido.getCriadoEm()).isEqualTo(AGORA);

        var aprovado = gateway.buscarPorOsId(orcamento.getOsId()).orElseThrow();
        var pagamento = aprovado.aprovar(AGORA, Duration.ofMinutes(30));
        pagamento.definirCobranca("pref-1", "https://mp.test/pagar");
        var atualizado = gateway.salvar(aprovado);
        assertThat(atualizado.getVersao()).isEqualTo(1);

        var comPagamento = gateway.buscarPorPagamentoId(pagamento.getId()).orElseThrow();
        assertThat(comPagamento.getId()).isEqualTo(orcamento.getId());
        assertThat(comPagamento.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(comPagamento.getItens()).hasSize(2);
        var lidoPagamento = comPagamento.getPagamento().orElseThrow();
        assertThat(lidoPagamento.getId()).isEqualTo(pagamento.getId());
        assertThat(lidoPagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(lidoPagamento.getValor()).isEqualByComparingTo("350.00");
        assertThat(lidoPagamento.getMpPreferenceId()).isEqualTo("pref-1");
        assertThat(lidoPagamento.getLinkPagamento()).isEqualTo("https://mp.test/pagar");
        assertThat(lidoPagamento.getExpiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
        assertThat(lidoPagamento.getTentativasEstorno()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from itens_orcamento", Integer.class)).isEqualTo(2);
    }

    @Test
    void buscaInexistenteRetornaVazio() {
        assertThat(gateway.buscarPorId(UUID.randomUUID())).isEmpty();
        assertThat(gateway.buscarPorOsId(UUID.randomUUID())).isEmpty();
        assertThat(gateway.buscarPorPagamentoId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void osIdDuplicadoViolaUnicidade() {
        var osId = UUID.randomUUID();
        gateway.salvar(novo(osId));

        assertThatThrownBy(() -> gateway.salvar(novo(osId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void atualizacaoConcorrenteLancaOptimisticLock() {
        var original = novo(UUID.randomUUID());
        gateway.salvar(original);
        var primeira = gateway.buscarPorId(original.getId()).orElseThrow();
        var segunda = gateway.buscarPorId(original.getId()).orElseThrow();
        primeira.recusar(AGORA);
        segunda.aprovar(AGORA, Duration.ofMinutes(30));

        gateway.salvar(primeira);

        assertThatThrownBy(() -> gateway.salvar(segunda))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(gateway.buscarPorId(original.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.RECUSADO);
    }

    @Test
    void buscarComPagamentoVencidoSoTrazPendentesVencidos() {
        var vencido = aprovado(AGORA.minus(Duration.ofMinutes(31)));
        var noPrazo = aprovado(AGORA.minus(Duration.ofMinutes(5)));
        var pago = aprovado(AGORA.minus(Duration.ofMinutes(40)));
        pago.confirmarPagamento("mp-1", AGORA);
        gateway.salvar(pago);
        var vencidoHaMaisTempo = aprovado(AGORA.minus(Duration.ofMinutes(35)));

        var resultado = gateway.buscarComPagamentoVencido(AGORA, 10);

        assertThat(resultado).extracting(Orcamento::getId)
                .containsExactly(vencidoHaMaisTempo.getId(), vencido.getId());
        assertThat(resultado.get(0).getItens()).hasSize(2);
        assertThat(gateway.buscarComPagamentoVencido(AGORA, 1)).hasSize(1);
        assertThat(noPrazo.getId()).isNotNull();
    }

    private Orcamento aprovado(Instant aprovadoEm) {
        var orcamento = novo(UUID.randomUUID());
        orcamento.aprovar(aprovadoEm, Duration.ofMinutes(30));
        return gateway.salvar(orcamento);
    }
}
