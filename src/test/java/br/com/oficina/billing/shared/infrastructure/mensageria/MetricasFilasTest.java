package br.com.oficina.billing.shared.infrastructure.mensageria;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricasFilasTest {

    private static final String FILA = "oficina-homolog-billing-commands-dlq";

    private final SqsAsyncClient sqs = mock(SqsAsyncClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private MetricasFilas metricas;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        metricas = new MetricasFilas(sqs, registry, FILA);
        logger = (Logger) LoggerFactory.getLogger(MetricasFilas.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("http://sqs/q").build()));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private void filaTem(String quantidade) {
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, quantidade)).build()));
    }

    private double gauge() {
        return registry.get("oficina.fila.dlq.mensagens").tag("fila", FILA).gauge().value();
    }

    @Test
    void gaugeLeOTamanhoDaDlq() {
        filaTem("3");

        metricas.atualizar();

        assertThat(gauge()).isEqualTo(3.0);
    }

    @Test
    void gaugeComecaEmZeroAntesDaPrimeiraLeitura() {
        assertThat(gauge()).isZero();
    }

    @Test
    void emFalhaMantemOUltimoValorELogaUmWarnPorSequencia() {
        filaTem("3");
        metricas.atualizar();
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sem rede")));

        metricas.atualizar();
        metricas.atualizar();

        assertThat(gauge()).isEqualTo(3.0);
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.ERROR).isEmpty();

        filaTem("5");
        metricas.atualizar();
        assertThat(gauge()).isEqualTo(5.0);

        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sem rede")));
        metricas.atualizar();
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(2);
    }

    @Test
    void chamadaQueNaoRespondeContaComoFalhaMantemOValorELogaUmWarn() {
        MetricasFilas lenta = new MetricasFilas(sqs, registry, "fila-lenta", Duration.ofMillis(50));
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(new CompletableFuture<>());

        lenta.atualizar();
        lenta.atualizar();

        assertThat(registry.get("oficina.fila.dlq.mensagens").tag("fila", "fila-lenta").gauge().value()).isZero();
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void falhaSemMensagemLogaONomeDaExcecao() {
        filaTem("1");
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));

        metricas.atualizar();

        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN)
                .singleElement().satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains("IllegalStateException").doesNotContain("null"));
    }

    @Test
    void scrapeDoPrometheusExpoeONomeDaMetrica() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new MetricasFilas(sqs, prometheus, FILA, Duration.ofSeconds(1));

        assertThat(prometheus.scrape()).contains("oficina_fila_dlq_mensagens{fila=\"" + FILA + "\"");
    }
}
