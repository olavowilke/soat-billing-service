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
 * <li>Falha do provedor: conta a tentativa e grava. Com menos de 3 tentativas devolve TENTAR_DE_NOVO,
 * sem resposta. Na terceira publica EstornoFalhou e devolve DESISTIU.</li>
 * </ul>
 */
public class EstornarPagamentoUseCase {

    static final int MAX_TENTATIVAS = 3;

    private static final Logger log = LoggerFactory.getLogger(EstornarPagamentoUseCase.class);

    private final OrcamentoGateway orcamentos;
    private final PagamentoGateway provedor;
    private final RespostaSagaGateway respostas;
    private final Clock clock;

    public EstornarPagamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway provedor,
            RespostaSagaGateway respostas, Clock clock) {
        this.orcamentos = orcamentos;
        this.provedor = provedor;
        this.respostas = respostas;
        this.clock = clock;
    }

    public ResultadoEstorno execute(UUID sagaId, UUID osId) {
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
            return registrarFalha(sagaId, osId, orcamento, pagamento, e);
        }
        orcamento.estornar(clock.instant());
        orcamentos.salvar(orcamento);
        respostas.pagamentoEstornado(sagaId, osId, pagamentoId);
        return ResultadoEstorno.ESTORNADO;
    }

    private ResultadoEstorno registrarFalha(UUID sagaId, UUID osId, Orcamento orcamento, Pagamento pagamento,
            RuntimeException erro) {
        pagamento.registrarFalhaEstorno();
        orcamentos.salvar(orcamento);
        int tentativas = pagamento.getTentativasEstorno();
        if (tentativas < MAX_TENTATIVAS) {
            log.warn("Estorno falhou no provedor, tentativa {} de {}: osId={}, erro={}", tentativas,
                    MAX_TENTATIVAS, osId, erro.getMessage());
            return ResultadoEstorno.TENTAR_DE_NOVO;
        }
        log.error("Estorno desistido apos {} tentativas: osId={}, pagamentoId={}", tentativas, osId,
                pagamento.getId(), erro);
        respostas.estornoFalhou(sagaId, osId, pagamento.getId(),
                "provedor recusou o estorno apos " + tentativas + " tentativas");
        return ResultadoEstorno.DESISTIU;
    }
}
