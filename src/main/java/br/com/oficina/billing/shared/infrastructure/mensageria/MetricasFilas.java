package br.com.oficina.billing.shared.infrastructure.mensageria;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lê a cada 60 s o tamanho da DLQ deste serviço e expõe
 * {@code oficina_fila_dlq_mensagens{fila}}. Cada chamada ao SQS tem prazo (o agendador é compartilhado com o relay da outbox e o job de expiração).
 * Se a leitura falha ou estoura o prazo, mantém o último valor
 * e escreve um WARN por sequência de falhas.
 */
@Component
public class MetricasFilas {

    private static final Logger log = LoggerFactory.getLogger(MetricasFilas.class);

    private final SqsAsyncClient sqs;
    private final String fila;
    private final Duration timeout;
    private final AtomicLong mensagens = new AtomicLong();
    private final AtomicBoolean emFalha = new AtomicBoolean();
    private volatile String url;

    @Autowired
    public MetricasFilas(SqsAsyncClient sqs, MeterRegistry registry, @Value("${billing.filas.comandos-dlq}") String fila) {
        this(sqs, registry, fila, Duration.ofSeconds(10));
    }

    MetricasFilas(SqsAsyncClient sqs, MeterRegistry registry, String fila, Duration timeout) {
        this.sqs = sqs;
        this.fila = fila;
        this.timeout = timeout;
        registry.gauge("oficina.fila.dlq.mensagens", Tags.of("fila", fila), mensagens);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void atualizar() {
        try {
            if (url == null) {
                url = aguardar(sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(fila).build())).queueUrl();
            }
            String valor = aguardar(sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(url)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build())).attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
            mensagens.set(Long.parseLong(valor));
            emFalha.set(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            avisar(e);
        } catch (Exception e) {
            avisar(e);
        }
    }

    /** Espera com prazo; se estoura, cancela a chamada para não deixar trabalho pendente. */
    private <T> T aguardar(CompletableFuture<T> chamada) throws Exception {
        try {
            return chamada.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            chamada.cancel(true);
            throw e;
        }
    }

    private void avisar(Exception e) {
        if (emFalha.compareAndSet(false, true)) {
            String motivo = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            log.warn("Não foi possível ler o tamanho da DLQ {}; mantém o último valor: {}", fila, motivo);
        }
    }
}
