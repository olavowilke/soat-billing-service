package br.com.oficina.billing.orcamento.gateways;

import br.com.oficina.billing.orcamento.entities.Orcamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistencia do orcamento (com itens e pagamento).
 *
 * <p>{@link #salvar(Orcamento)} usa lock otimista pela versao do orcamento. Se outra transacao gravou antes,
 * a implementacao lanca ObjectOptimisticLockingFailureException (Spring). Os casos de uso dos proximos tasks
 * tratam essa excecao fora desta porta.
 */
public interface OrcamentoGateway {

    Orcamento salvar(Orcamento orcamento);

    Optional<Orcamento> buscarPorId(UUID id);

    Optional<Orcamento> buscarPorOsId(UUID osId);

    Optional<Orcamento> buscarPorPagamentoId(UUID pagamentoId);

    /** Orcamentos aprovados cujo pagamento esta PENDENTE e ja venceu, do mais antigo para o mais novo. */
    List<Orcamento> buscarComPagamentoVencido(Instant agora, int limite);
}
