package br.com.oficina.billing.shared.infrastructure.mensageria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Le a outbox e envia as respostas para a fila do OS Service.
 *
 * <p>Decisao: o envio ao SQS acontece dentro da transacao do relay, que segura o lock das linhas
 * (FOR UPDATE SKIP LOCKED). No volume deste servico (lote de 50, uma chamada SQS por linha) o lock dura
 * poucos segundos e o cliente AWS tem timeout. Assim uma linha so ganha {@code enviado_em} se o SQS aceitou,
 * e duas replicas nunca enviam a mesma linha. Se o SQS aceita e o commit falha, a linha vai de novo:
 * a entrega e at-least-once e o consumidor deduplica pelo messageId.
 *
 * <p>Uma falha nao para a rodada e nao trava as outras sagas. A linha que falhou espera um backoff
 * (1s, 2s, 4s ... ate 5 min) e bloqueia so as linhas mais novas da mesma saga (ordem por saga).
 * O metodo agendado e o proprio @Transactional: o scheduler chama o proxy, entao o lock e o update
 * ficam na mesma transacao.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int LOTE = 50;

    private final OutboxJpaRepository repository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper mapper;
    private final String filaRespostas;

    public OutboxRelay(OutboxJpaRepository repository, SqsTemplate sqsTemplate, ObjectMapper mapper,
            @Value("${billing.filas.respostas}") String filaRespostas) {
        this.repository = repository;
        this.sqsTemplate = sqsTemplate;
        this.mapper = mapper;
        this.filaRespostas = filaRespostas;
    }

    @Scheduled(fixedDelayString = "${billing.outbox.intervalo:PT1S}", initialDelayString = "${billing.outbox.intervalo:PT1S}")
    @Transactional
    public void enviarPendentes() {
        for (var linha : repository.travarPendentes(LOTE)) {
            try {
                enviar(linha);
                linha.marcarEnviado(Instant.now());
            } catch (Exception e) {
                linha.registrarFalha(Instant.now());
                log.warn("Falha ao enviar outbox id={} tipo={} tentativa={} proxima={}: {}", linha.getId(),
                        linha.getTipo(), linha.getTentativas(), linha.getProximaTentativaEm(), e.toString());
            }
        }
    }

    /** Le o trace gravado com a linha. Sem valor, a mensagem sai sem o atributo newrelic. */
    private Map<String, String> lerTrace(OutboxData linha) throws JsonProcessingException {
        var bruto = linha.getTraceHeaders();
        if (bruto == null || bruto.isBlank()) {
            return Map.of();
        }
        return mapper.readValue(bruto, new TypeReference<Map<String, String>>() {
        });
    }

    /** SQS recusa atributo vazio. Uma linha sem correlationId usa o id da propria linha. */
    private static String correlationId(OutboxData linha) {
        var valor = linha.getCorrelationId();
        return valor == null || valor.isBlank() ? linha.getId().toString() : valor;
    }

    private void enviar(OutboxData linha) throws JsonProcessingException {
        var envelope = new MensagemSaga(linha.getId(), linha.getSagaId(), linha.getOsId(), linha.getTipo(),
                linha.getCorrelationId(), linha.getCriadoEm(), mapper.readTree(linha.getPayload()));
        var corpo = mapper.writeValueAsString(envelope);
        var trace = lerTrace(linha);
        sqsTemplate.send(to -> {
            to.queue(filaRespostas).payload(corpo)
                    .header("type", linha.getTipo())
                    .header("correlationId", correlationId(linha));
            trace.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    to.header(k, v);
                }
            });
        });
    }
}
