package br.com.oficina.billing.shared.infrastructure.mensageria;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Envelope de toda mensagem da saga. Ver contrato em "Message envelope". */
public record MensagemSaga(
        UUID messageId,
        UUID sagaId,
        UUID osId,
        String type,
        String correlationId,
        Instant occurredAt,
        JsonNode payload) {
}
