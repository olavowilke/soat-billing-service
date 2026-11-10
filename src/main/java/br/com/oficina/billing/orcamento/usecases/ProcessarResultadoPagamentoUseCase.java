package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplica o resultado do pagamento vindo do provedor (webhook ou endpoint fake).
 *
 * <ul>
 * <li>Situacao PENDENTE: nada a fazer.</li>
 * <li>Pagamento PENDENTE + APROVADO: confirma e responde PagamentoConfirmado.</li>
 * <li>Pagamento PENDENTE + REJEITADO: rejeita e responde PagamentoFalhou "REJEITADO".</li>
 * <li>Pagamento ja APROVADO ou REJEITADO: repeticao, nada a fazer.</li>
 * <li>Pagamento EXPIRADO + APROVADO: estorna na hora, sem resposta a saga (a saga ja foi cancelada).</li>
 * <li>Pagamento desconhecido: WARN e nada a fazer.</li>
 * </ul>
 */
public class ProcessarResultadoPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarResultadoPagamentoUseCase.class);

    private final OrcamentoGateway orcamentos;
    private final PagamentoGateway provedor;
    private final RespostaSagaGateway respostas;
    private final Clock clock;

    public ProcessarResultadoPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway provedor,
            RespostaSagaGateway respostas, Clock clock) {
        this.orcamentos = orcamentos;
        this.provedor = provedor;
        this.respostas = respostas;
        this.clock = clock;
    }

    public ResultadoProcessamento execute(UUID pagamentoId, String mpPaymentId, SituacaoPagamentoExterno situacao) {
        if (situacao == SituacaoPagamentoExterno.APROVADO && (mpPaymentId == null || mpPaymentId.isBlank())) {
            throw new IllegalArgumentException("mpPaymentId e obrigatorio para pagamento aprovado");
        }
        if (situacao == SituacaoPagamentoExterno.PENDENTE) {
            return ResultadoProcessamento.IGNORADO;
        }
        Optional<Orcamento> encontrado = orcamentos.buscarPorPagamentoId(pagamentoId);
        if (encontrado.isEmpty()) {
            log.warn("Resultado de pagamento desconhecido ignorado: pagamentoId={}, situacao={}", pagamentoId,
                    situacao);
            return ResultadoProcessamento.DESCONHECIDO;
        }
        Orcamento orcamento = encontrado.get();
        Pagamento pagamento = orcamento.getPagamento().orElseThrow();
        StatusPagamento status = pagamento.getStatus();

        if (status == StatusPagamento.PENDENTE) {
            return aplicar(orcamento, pagamento, mpPaymentId, situacao);
        }
        if (status == StatusPagamento.EXPIRADO && situacao == SituacaoPagamentoExterno.APROVADO) {
            log.warn("Pagamento aprovado depois de expirar, estornando: pagamentoId={}, mpPaymentId={}",
                    pagamentoId, mpPaymentId);
            provedor.estornar(mpPaymentId, pagamentoId);
            return ResultadoProcessamento.ESTORNADO;
        }
        log.info("Resultado repetido ou tardio ignorado: pagamentoId={}, status={}, situacao={}", pagamentoId,
                status, situacao);
        return ResultadoProcessamento.IGNORADO;
    }

    private ResultadoProcessamento aplicar(Orcamento orcamento, Pagamento pagamento, String mpPaymentId,
            SituacaoPagamentoExterno situacao) {
        if (situacao == SituacaoPagamentoExterno.APROVADO) {
            orcamento.confirmarPagamento(mpPaymentId, clock.instant());
            orcamentos.salvar(orcamento);
            respostas.pagamentoConfirmado(orcamento.getSagaId(), orcamento.getCorrelationId(), orcamento.getOsId(), orcamento.getId(),
                    pagamento.getId());
        } else {
            orcamento.rejeitarPagamento(clock.instant());
            orcamentos.salvar(orcamento);
            respostas.pagamentoFalhou(orcamento.getSagaId(), orcamento.getCorrelationId(), orcamento.getOsId(), orcamento.getId(),
                    pagamento.getId(), "REJEITADO");
        }
        return ResultadoProcessamento.APLICADO;
    }
}
