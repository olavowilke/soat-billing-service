package br.com.oficina.billing.orcamento.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "estornos_avulsos")
class EstornoAvulsoData {

    @Id
    private UUID id;

    @Column(name = "pagamento_id", nullable = false)
    private UUID pagamentoId;

    @Column(name = "mp_payment_id", nullable = false, length = 100)
    private String mpPaymentId;

    @Column(nullable = false, length = 50)
    private String motivo;

    @Column(nullable = false)
    private boolean sucesso;

    @Column(length = 300)
    private String erro;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected EstornoAvulsoData() {
    }

    EstornoAvulsoData(UUID id, UUID pagamentoId, String mpPaymentId, String motivo, boolean sucesso, String erro,
            Instant criadoEm) {
        this.id = id;
        this.pagamentoId = pagamentoId;
        this.mpPaymentId = mpPaymentId;
        this.motivo = motivo;
        this.sucesso = sucesso;
        this.erro = erro;
        this.criadoEm = criadoEm;
    }
}
