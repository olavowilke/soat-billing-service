package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Prova o agendador real: ele chama o metodo transacional pelo proxy, grava {@code enviado_em} e nao reenvia.
 * Este contexto tem intervalo curto e e descartado no fim da classe, para o seu relay nao enviar linhas de
 * outros testes.
 */
@TestPropertySource(properties = "billing.outbox.intervalo=PT0.3S")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxRelaySchedulerIT extends BillingIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SqsAsyncClient sqs;

    private UUID inserir(UUID sagaId, String tipo, int segundosAtras) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into outbox (id, tipo, saga_id, os_id, correlation_id, payload, criado_em)
                values (?, ?, ?, ?, ?, '{}'::jsonb, ?)
                """, id, tipo, sagaId, UUID.randomUUID(), "corr-" + id,
                Timestamp.from(Instant.now().minusSeconds(segundosAtras)));
        return id;
    }

    private boolean enviada(UUID id) {
        return jdbc.queryForObject("select enviado_em is not null from outbox where id = ?", Boolean.class, id);
    }

    private void coletar(String queueUrl, Set<UUID> sagas, List<Message> destino) {
        var resposta = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl)
                .messageAttributeNames("All").waitTimeSeconds(1).maxNumberOfMessages(10).build()).join();
        resposta.messages().stream()
                .filter(m -> sagas.stream().anyMatch(s -> m.body().contains(s.toString())))
                .forEach(destino::add);
    }

    @Test
    void relayEnviaLinhasPendentesEMarcaEnviadas() {
        jdbc.update("delete from outbox");
        var saga1 = UUID.randomUUID();
        var saga2 = UUID.randomUUID();
        var primeira = inserir(saga1, "OrcamentoGerado", 2);
        var segunda = inserir(saga2, "OrcamentoRecusado", 1);
        var queueUrl = sqs.getQueueUrl(b -> b.queueName(FILA_RESPOSTAS)).join().queueUrl();
        var recebidas = new ArrayList<Message>();

        await().atMost(Duration.ofSeconds(20)).until(() -> {
            coletar(queueUrl, Set.of(saga1, saga2), recebidas);
            return recebidas.size() >= 2;
        });
        await().atMost(Duration.ofSeconds(10)).until(() -> enviada(primeira) && enviada(segunda));

        assertThat(recebidas).extracting(m -> m.messageAttributes().get("type").stringValue())
                .containsExactlyInAnyOrder("OrcamentoGerado", "OrcamentoRecusado");
        // Passam varias rodadas. Se o update nao fosse gravado, as linhas voltariam para a fila.
        var extras = new ArrayList<Message>();
        for (int i = 0; i < 3; i++) {
            coletar(queueUrl, Set.of(saga1, saga2), extras);
        }
        assertThat(extras).isEmpty();
        jdbc.update("delete from outbox");
    }
}
