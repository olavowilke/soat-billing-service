package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.BillingIT;
import br.com.oficina.billing.shared.infrastructure.mensageria.OutboxData;
import br.com.oficina.billing.shared.infrastructure.mensageria.OutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le a linha dentro da transacao que a grava, antes de o relay enviar. */
class RespostaSagaGatewayOutboxIT extends BillingIT {

    private final UUID sagaId = UUID.randomUUID();
    private final UUID osId = UUID.randomUUID();
    private final UUID orcamentoId = UUID.randomUUID();
    private final UUID pagamentoId = UUID.randomUUID();

    @Autowired
    private RespostaSagaGateway gateway;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OutboxJpaRepository outbox;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void limpar() {
        jdbc.update("delete from outbox");
        MDC.clear();
    }

    @AfterEach
    void limparDepois() {
        jdbc.update("delete from outbox");
        MDC.clear();
    }

    private OutboxData gravar(Runnable acao) {
        return tx.execute(status -> {
            acao.run();
            return outbox.findAll().stream().filter(l -> l.getSagaId().equals(sagaId)).findFirst().orElseThrow();
        });
    }

    private JsonNode json(OutboxData linha) throws Exception {
        return mapper.readTree(linha.getPayload());
    }

    @Test
    void orcamentoGeradoGravaPayloadDoContratoComOIdDaSaga() throws Exception {
        // O id explicito da saga vence o id da requisicao no MDC.
        MDC.put("correlationId", "corr-da-requisicao");

        var linha = gravar(() -> gateway.orcamentoGerado(sagaId, "corr-123", osId, orcamentoId, new BigDecimal("350.5")));

        assertThat(linha.getTipo()).isEqualTo("OrcamentoGerado");
        assertThat(linha.getSagaId()).isEqualTo(sagaId);
        assertThat(linha.getOsId()).isEqualTo(osId);
        assertThat(linha.getCorrelationId()).isEqualTo("corr-123");
        var payload = json(linha);
        assertThat(payload.get("orcamentoId").asText()).isEqualTo(orcamentoId.toString());
        assertThat(payload.get("valorTotal").isTextual()).isTrue();
        assertThat(payload.get("valorTotal").asText()).isEqualTo("350.50");
    }

    @Test
    void semIdDaSagaNemMdcUsaUuidComoCorrelationId() {
        var linha = gravar(() -> gateway.orcamentoRecusado(sagaId, null, osId, orcamentoId));

        assertThat(UUID.fromString(linha.getCorrelationId())).isNotNull();
    }

    @Test
    void cadaRespostaGravaTipoEPayloadDoContrato() throws Exception {
        var casos = Map.<String, Runnable>of(
                "OrcamentoFalhou", () -> gateway.orcamentoFalhou(sagaId, "corr-123", osId, "sem itens"),
                "OrcamentoAprovado", () -> gateway.orcamentoAprovado(sagaId, "corr-123", osId, orcamentoId, pagamentoId, "http://x"),
                "OrcamentoRecusado", () -> gateway.orcamentoRecusado(sagaId, "corr-123", osId, orcamentoId),
                "PagamentoConfirmado", () -> gateway.pagamentoConfirmado(sagaId, "corr-123", osId, orcamentoId, pagamentoId),
                "PagamentoFalhou", () -> gateway.pagamentoFalhou(sagaId, "corr-123", osId, orcamentoId, pagamentoId, "EXPIRADO"),
                "PagamentoEstornado", () -> gateway.pagamentoEstornado(sagaId, "corr-123", osId, pagamentoId),
                "EstornoFalhou", () -> gateway.estornoFalhou(sagaId, "corr-123", osId, pagamentoId, "MP fora"));
        var campos = Map.of(
                "OrcamentoFalhou", "[motivo]",
                "OrcamentoAprovado", "[linkPagamento, orcamentoId, pagamentoId]",
                "OrcamentoRecusado", "[orcamentoId]",
                "PagamentoConfirmado", "[orcamentoId, pagamentoId]",
                "PagamentoFalhou", "[motivo, orcamentoId, pagamentoId]",
                "PagamentoEstornado", "[pagamentoId]",
                "EstornoFalhou", "[motivo, pagamentoId]");
        for (var caso : casos.entrySet()) {
            jdbc.update("delete from outbox");
            var linha = gravar(caso.getValue());
            assertThat(linha.getTipo()).isEqualTo(caso.getKey());
            var nomes = new java.util.TreeSet<String>();
            json(linha).fieldNames().forEachRemaining(nomes::add);
            assertThat(nomes.toString()).as(caso.getKey()).isEqualTo(campos.get(caso.getKey()));
        }
    }

    @Test
    void semTransacaoDoChamadorFalha() {
        assertThatThrownBy(() -> gateway.orcamentoRecusado(sagaId, "corr-123", osId, orcamentoId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
