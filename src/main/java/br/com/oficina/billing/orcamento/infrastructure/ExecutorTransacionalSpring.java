package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.gateways.ExecutorTransacional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Uma transacao nova por chamada. Um conflito de lock otimista desfaz a transacao e devolve false. */
@Component
class ExecutorTransacionalSpring implements ExecutorTransacional {

    private final TransactionTemplate tx;

    ExecutorTransacionalSpring(TransactionTemplate tx) {
        this.tx = tx;
    }

    @Override
    public boolean executar(Runnable acao) {
        try {
            tx.executeWithoutResult(status -> acao.run());
            return true;
        } catch (OptimisticLockingFailureException e) {
            return false;
        }
    }
}
