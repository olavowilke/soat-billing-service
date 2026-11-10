package br.com.oficina.billing.shared.infrastructure.mensageria;

import br.com.oficina.billing.shared.BillingIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemProcessadaIT extends BillingIT {

    @Autowired
    private MensagemProcessadaJpaRepository repository;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void registraUmaVezSoEIgnoraRepeticao() {
        var id = UUID.randomUUID();

        var primeira = tx.execute(s -> repository.registrar(id, Instant.now()));
        var segunda = tx.execute(s -> repository.registrar(id, Instant.now()));

        assertThat(primeira).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    void registroDesfeitoComATransacao() {
        var id = UUID.randomUUID();

        tx.execute(s -> {
            repository.registrar(id, Instant.now());
            s.setRollbackOnly();
            return null;
        });

        assertThat(repository.existsById(id)).isFalse();
    }

    @Test
    void semTransacaoDoChamadorFalha() {
        assertThatThrownBy(() -> repository.registrar(UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
