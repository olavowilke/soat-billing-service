package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.EstornoAvulsoGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Grava em transacao propria (REQUIRES_NEW): a linha sobrevive se a transacao do chamador desfizer. */
@Component
class EstornoAvulsoGatewayImpl implements EstornoAvulsoGateway {

    private static final int LIMITE_ERRO = 300;

    private final EstornoAvulsoJpaRepository repository;
    private final Clock clock;

    EstornoAvulsoGatewayImpl(EstornoAvulsoJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UUID pagamentoId, String mpPaymentId, String motivo, boolean sucesso, String erro) {
        String corte = erro == null || erro.length() <= LIMITE_ERRO ? erro : erro.substring(0, LIMITE_ERRO);
        repository.save(new EstornoAvulsoData(UUID.randomUUID(), pagamentoId, mpPaymentId, motivo, sucesso, corte,
                clock.instant()));
    }
}
