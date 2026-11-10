package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.ResultadoConsultaPagamento;
import br.com.oficina.billing.orcamento.infrastructure.mercadopago.AssinaturaWebhook;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook do Mercado Pago. So existe com billing.mercadopago.modo=real; em outro modo a rota responde 404.
 *
 * <p>A rota e publica, entao a assinatura (x-signature) e a unica defesa. O webhook so avisa que algo mudou:
 * o Billing consulta o pagamento na API do Mercado Pago, FORA de qualquer transacao, e so entao abre a transacao
 * para aplicar o resultado. Se a consulta falhar, a resposta e 502 e o Mercado Pago repete o aviso.
 * Pagamento que nao e nosso ou desconhecido recebe 200, para o Mercado Pago parar de repetir.
 */
@RestController
@RequestMapping("/public/webhooks/mercadopago")
@ConditionalOnProperty(name = "billing.mercadopago.modo", havingValue = "real")
@Tag(name = "Webhook Mercado Pago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final ProcessarResultadoPagamentoUseCase processar;
    private final PagamentoGateway provedor;
    private final TransactionTemplate tx;
    private final String segredo;

    public MercadoPagoWebhookController(ProcessarResultadoPagamentoUseCase processar, PagamentoGateway provedor,
            TransactionTemplate tx, @Value("${billing.mercadopago.webhook-secret:}") String segredo) {
        this.processar = processar;
        this.provedor = provedor;
        this.tx = tx;
        this.segredo = segredo;
    }

    @PostMapping
    @Operation(summary = "Recebe o aviso de pagamento do Mercado Pago")
    public ResponseEntity<Void> receber(
            @RequestParam(name = "type", required = false) String tipo,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId) {
        if (!AssinaturaWebhook.valida(assinatura, requestId, dataId, segredo)) {
            log.warn("Webhook do Mercado Pago recusado: assinatura invalida ou ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"payment".equals(tipo)) {
            log.info("Webhook do Mercado Pago ignorado: type={}", tipo);
            return ResponseEntity.ok().build();
        }
        ResultadoConsultaPagamento consulta = provedor.consultar(dataId);
        if (consulta.pagamentoId() == null) {
            log.warn("Pagamento do Mercado Pago sem referencia nossa, ignorado: mpPaymentId={}", dataId);
            return ResponseEntity.ok().build();
        }
        ResultadoProcessamento resultado = tx.execute(status -> processar.execute(consulta.pagamentoId(),
                consulta.mpPaymentId(), consulta.situacao()));
        log.info("Webhook do Mercado Pago processado: pagamentoId={}, situacao={}, resultado={}",
                consulta.pagamentoId(), consulta.situacao(), resultado);
        return ResponseEntity.ok().build();
    }
}
