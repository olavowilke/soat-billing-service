package br.com.oficina.billing.shared.infrastructure.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxData {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "os_id", nullable = false)
    private UUID osId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    /** Mapa JSON dos atributos de trace (por exemplo {"newrelic": "..."}) capturado quando a linha foi gravada. */
    @Column(name = "trace_headers", columnDefinition = "text")
    private String traceHeaders;

    protected OutboxData() {
    }

    public OutboxData(UUID id, String tipo, UUID sagaId, UUID osId, String correlationId, String payload,
            Instant criadoEm) {
        this.id = id;
        this.tipo = tipo;
        this.sagaId = sagaId;
        this.osId = osId;
        this.correlationId = correlationId;
        this.payload = payload;
        this.criadoEm = criadoEm;
    }

    public OutboxData(UUID id, String tipo, UUID sagaId, UUID osId, String correlationId, String payload,
            Instant criadoEm, String traceHeaders) {
        this(id, tipo, sagaId, osId, correlationId, payload, criadoEm);
        this.traceHeaders = traceHeaders;
    }

    public String getTraceHeaders() {
        return traceHeaders;
    }

    public UUID getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public UUID getOsId() {
        return osId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Instant getProximaTentativaEm() {
        return proximaTentativaEm;
    }

    /** Conta a falha e adia a proxima tentativa: 1s, 2s, 4s ... ate 5 min. */
    public void registrarFalha(Instant agora) {
        tentativas++;
        long segundos = Math.min(300L, 1L << Math.min(tentativas - 1, 20));
        proximaTentativaEm = agora.plusSeconds(segundos);
    }

    public void marcarEnviado(Instant quando) {
        this.enviadoEm = quando;
    }
}
