package br.com.oficina.billing.shared.infrastructure.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagens_processadas")
public class MensagemProcessadaData {

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "processada_em", nullable = false)
    private Instant processadaEm;

    protected MensagemProcessadaData() {
    }

    public MensagemProcessadaData(UUID messageId, Instant processadaEm) {
        this.messageId = messageId;
        this.processadaEm = processadaEm;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public Instant getProcessadaEm() {
        return processadaEm;
    }
}
