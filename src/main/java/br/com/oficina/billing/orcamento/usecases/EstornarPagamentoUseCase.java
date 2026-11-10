package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;

/**
 * Estorna o pagamento de uma OS (comando EstornarPagamento da saga).
 *
 * <ul>
 * <li>Orcamento ja ESTORNADO: repete a resposta PagamentoEstornado.</li>
 * <li>Orcamento que nao esta PAGO (ou OS sem orcamento): EstornoFalhou e DESISTIU. Repetir nao ajuda.</li>
 * <li>Pagamento sem mpPaymentId: EstornoFalhou e DESISTIU.</li>
 * <li>Falha do provedor: conta a tentativa (auditoria) e grava. Se a entrega e a ultima que a fila permite
 * (receiveCount &gt;= maxEntregas), publica EstornoFalhou e devolve DESISTIU. Antes disso devolve TENTAR_DE_NOVO,
 * sem resposta. A decisao usa o numero de entregas da fila, nao so as tentativas gravadas: uma entrega que nao
 * grava (timeout, erro de banco) tambem gasta um recebimento, e a mensagem iria para a DLQ sem resposta.</li>
 * </ul>
 */
public class EstornarPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(EstornarPagamentoUseCase.class);

    private final OrcamentoGateway orcamentos;
    private final PagamentoGateway provedor;
    private final RespostaSagaGateway respostas;
    private final Clock clock;
    private final int maxEntregas;

    public EstornarPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway provedor,
            RespostaSagaGateway respostas, Clock clock, int maxEntregas) {
        this.orcamentos = orcamentos;
        this.provedor = provedor;
        this.respostas = respostas;
        this.clock = clock;
        this.maxEntregas = maxEntregas;
    }

    /** @param receiveCount numero desta entrega na fila (ApproximateReceiveCount), 1 na primeira. */
    public ResultadoEstorno execute(UUID sagaId, UUID osId, int receiveCount) {
        var existente = orcamentos.buscarPorOsId(osId);
        if (existente.isEmpty()) {
            log.warn("EstornarPagamento para OS sem orcamento: osId={}", osId);
            respostas.estornoFalhou(sagaId, osId, null, "OS sem orcamento");
            return ResultadoEstorno.DESISTIU;
        }
        Orcamento orcamento = existente.get();
        Pagamento pagamento = orcamento.getPagamento().orElse(null);
        UUID pagamentoId = pagamento == null ? null : pagamento.getId();

        if (orcamento.getStatus() == StatusOrcamento.ESTORNADO) {
            respostas.pagamentoEstornado(sagaId, osId, pagamentoId);
            return ResultadoEstorno.ESTORNADO;
        }
        if (orcamento.getStatus() != StatusOrcamento.PAGO || pagamento == null) {
            log.warn("EstornarPagamento para orcamento fora de PAGO: osId={}, status={}", osId,
                    orcamento.getStatus());
            respostas.estornoFalhou(sagaId, osId, pagamentoId,
                    "orcamento nao esta pago (status " + orcamento.getStatus() + ")");
            return ResultadoEstorno.DESISTIU;
        }
        String mpPaymentId = pagamento.getMpPaymentId();
        if (mpPaymentId == null || mpPaymentId.isBlank()) {
            log.error("Pagamento pago sem mpPaymentId, estorno impossivel: osId={}, pagamentoId={}", osId,
                    pagamentoId);
            respostas.estornoFalhou(sagaId, osId, pagamentoId, "pagamento sem identificador no provedor");
            return ResultadoEstorno.DESISTIU;
        }

        try {
            provedor.estornar(mpPaymentId, pagamentoId);
        } catch (RuntimeException e) {
            return registrarFalha(sagaId, osId, orcamento, pagamento, receiveCount, e);
        }
        orcamento.estornar(clock.instant());
        orcamentos.salvar(orcamento);
        respostas.pagamentoEstornado(sagaId, osId, pagamentoId);
        return ResultadoEstorno.ESTORNADO;
    }

    private ResultadoEstorno registrarFalha(UUID sagaId, UUID osId, Orcamento orcamento, Pagamento pagamento,
            int receiveCount, RuntimeException erro) {
        pagamento.registrarFalhaEstorno();
        orcamentos.salvar(orcamento);
        int tentativas = pagamento.getTentativasEstorno();
        // Se o contador do SQS faltar (0), as tentativas gravadas servem de piso.
        int entregas = Math.max(receiveCount, tentativas);
        if (entregas < maxEntregas) {
            log.warn("Estorno falhou no provedor, entrega {} de {}: osId={}, erro={}", entregas,
                    maxEntregas, osId, erro.getMessage());
            return ResultadoEstorno.TENTAR_DE_NOVO;
        }
        log.error("Estorno desistido na entrega {} ({} tentativas gravadas): osId={}, pagamentoId={}", entregas,
                tentativas, osId,
                pagamento.getId(), erro);
        respostas.estornoFalhou(sagaId, osId, pagamento.getId(),
                "provedor recusou o estorno apos " + entregas + " entregas");
        return ResultadoEstorno.DESISTIU;
    }
}
