package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/** Auditoria dos estornos feitos fora da saga (pagamento aprovado quando o pagamento ja nao esta pendente). */
class EstornoAvulsoIT extends BillingIT {

    @Autowired
    private ProcessarResultadoPagamentoUseCase processar;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc.update("delete from estornos_avulsos");
    }

    private Orcamento rejeitado() {
        Instant agora = Instant.now();
        Orcamento o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), "corr-" + UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Servico", 1,
                        new BigDecimal("100.00"))), agora);
        o.aprovar(agora, Duration.ofMinutes(30)).definirCobranca("pref", "https://pagar");
        o.rejeitarPagamento(agora);
        return orcamentoGateway.salvar(o);
    }

    @Test
    void estornoTardioGravaLinhaDeAuditoriaComSucesso() {
        Orcamento o = rejeitado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();

        tx.executeWithoutResult(s -> processar.execute(pagamentoId, "mp-novo", SituacaoPagamentoExterno.APROVADO));

        Map<String, Object> linha = jdbc.queryForMap("select * from estornos_avulsos where pagamento_id = ?",
                pagamentoId);
        assertThat(linha.get("mp_payment_id")).isEqualTo("mp-novo");
        assertThat(linha.get("motivo")).isEqualTo("APROVADO_COM_PAGAMENTO_REJEITADO");
        assertThat(linha.get("sucesso")).isEqualTo(true);
        assertThat(linha.get("erro")).isNull();
        assertThat(linha.get("criado_em")).isNotNull();
    }

    @Test
    void falhaDoProvedorGravaLinhaComErroEPropagaExcecao() {
        Orcamento o = rejeitado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();
        doThrow(new IllegalStateException("provedor fora")).when(pagamentoGateway).estornar(eq("mp-novo"), any());

        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> processar.execute(pagamentoId, "mp-novo", SituacaoPagamentoExterno.APROVADO)))
                .isInstanceOf(IllegalStateException.class);

        Map<String, Object> linha = jdbc.queryForMap("select * from estornos_avulsos where pagamento_id = ?",
                pagamentoId);
        assertThat(linha.get("sucesso")).isEqualTo(false);
        assertThat(linha.get("erro")).isEqualTo("provedor fora");
    }
}
