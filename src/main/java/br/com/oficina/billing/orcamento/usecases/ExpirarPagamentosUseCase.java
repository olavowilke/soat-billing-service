package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.gateways.ExecutorTransacional;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Expira os pagamentos PENDENTES vencidos e responde PagamentoFalhou "EXPIRADO" para cada um.
 *
 * <p>Cada orcamento roda em uma transacao propria (porta {@link ExecutorTransacional}). A transacao le o
 * orcamento de novo, porque o resultado do provedor pode ter chegado depois da lista. Se outra transacao gravou
 * a mesma linha antes (lock otimista), a linha e pulada com WARN e fica para a proxima rodada. Uma falha
 * inesperada em uma linha tambem nao para as outras.
 */
public class ExpirarPagamentosUseCase {

    static final int LIMITE_POR_RODADA = 50;

    private static final Logger log = LoggerFactory.getLogger(ExpirarPagamentosUseCase.class);

    private final OrcamentoGateway orcamentos;
    private final RespostaSagaGateway respostas;
    private final ExecutorTransacional transacao;

    public ExpirarPagamentosUseCase(OrcamentoGateway orcamentos, RespostaSagaGateway respostas,
            ExecutorTransacional transacao) {
        this.orcamentos = orcamentos;
        this.respostas = respostas;
        this.transacao = transacao;
    }

    /** @return quantos pagamentos esta rodada expirou. */
    public int execute(Instant agora) {
        List<Orcamento> vencidos = orcamentos.buscarComPagamentoVencido(agora, LIMITE_POR_RODADA);
        int expirados = 0;
        for (Orcamento candidato : vencidos) {
            UUID id = candidato.getId();
            AtomicBoolean expirou = new AtomicBoolean();
            try {
                boolean confirmou = transacao.executar(() -> expirou.set(expirar(id, agora)));
                if (!confirmou) {
                    log.warn("Orcamento {} foi alterado por outra transacao durante a expiracao, "
                            + "linha pulada ate a proxima rodada", id);
                } else if (expirou.get()) {
                    expirados++;
                }
            } catch (RuntimeException e) {
                log.error("Falha ao expirar o pagamento do orcamento {}, linha pulada", id, e);
            }
        }
        return expirados;
    }

    /** @return true se expirou; false se a linha nao esta mais pendente. */
    private boolean expirar(UUID orcamentoId, Instant agora) {
        Orcamento orcamento = orcamentos.buscarPorId(orcamentoId).orElse(null);
        if (orcamento == null || orcamento.getStatus() != StatusOrcamento.APROVADO) {
            return false;
        }
        Pagamento pagamento = orcamento.getPagamento().orElse(null);
        if (pagamento == null || pagamento.getStatus() != StatusPagamento.PENDENTE) {
            return false;
        }
        orcamento.expirarPagamento(agora);
        orcamentos.salvar(orcamento);
        respostas.pagamentoFalhou(orcamento.getSagaId(), orcamento.getCorrelationId(), orcamento.getOsId(),
                orcamento.getId(), pagamento.getId(), "EXPIRADO");
        return true;
    }
}
