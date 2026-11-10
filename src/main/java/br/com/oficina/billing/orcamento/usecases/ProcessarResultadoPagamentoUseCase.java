package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.gateways.EstornoAvulsoGateway;
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
 * <li>Pagamento nao PENDENTE + APROVADO, exceto o mesmo mpPaymentId ja confirmado: estorna na hora, sem
 * resposta a saga, e audita em estornos_avulsos. Se o provedor falhar, audita e a excecao sobe, para o
 * webhook responder erro e o provedor repetir.</li>
 * <li>Pagamento APROVADO ou ESTORNADO + APROVADO com o mesmo mpPaymentId: webhook repetido, nada a fazer.</li>
 * <li>Pagamento desconhecido: WARN e nada a fazer.</li>
 * </ul>
 */
public class ProcessarResultadoPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarResultadoPagamentoUseCase.class);

    private final OrcamentoGateway orcamentos;
    private final PagamentoGateway provedor;
    private final RespostaSagaGateway respostas;
    private final EstornoAvulsoGateway avulsos;
    private final Clock clock;

    public ProcessarResultadoPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway provedor,
            RespostaSagaGateway respostas, EstornoAvulsoGateway avulsos, Clock clock) {
        this.orcamentos = orcamentos;
        this.provedor = provedor;
        this.respostas = respostas;
        this.avulsos = avulsos;
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
        if (situacao == SituacaoPagamentoExterno.APROVADO && !duplicadoDoMesmoPagamento(pagamento, mpPaymentId)) {
            log.warn("Pagamento aprovado com pagamento em {}, estornando: pagamentoId={}, mpPaymentId={}", status,
                    pagamentoId, mpPaymentId);
            estornarAvulso(pagamentoId, mpPaymentId, "APROVADO_COM_PAGAMENTO_" + status);
            return ResultadoProcessamento.ESTORNADO;
        }
        log.info("Resultado repetido ou tardio ignorado: pagamentoId={}, status={}, situacao={}", pagamentoId,
                status, situacao);
        return ResultadoProcessamento.IGNORADO;
    }

    private static boolean duplicadoDoMesmoPagamento(Pagamento pagamento, String mpPaymentId) {
        boolean confirmado = pagamento.getStatus() == StatusPagamento.APROVADO
                || pagamento.getStatus() == StatusPagamento.ESTORNADO;
        return confirmado && mpPaymentId.equals(pagamento.getMpPaymentId());
    }

    private void estornarAvulso(UUID pagamentoId, String mpPaymentId, String motivo) {
        try {
            provedor.estornar(mpPaymentId, pagamentoId);
        } catch (RuntimeException e) {
            avulsos.registrar(pagamentoId, mpPaymentId, motivo, false, e.getMessage());
            throw e;
        }
        avulsos.registrar(pagamentoId, mpPaymentId, motivo, true, null);
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
