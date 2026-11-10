package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.infrastructure.fake.FakePagamentoGateway;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pagamento de mentira para a demo. So existe com billing.mercadopago.modo=fake; em outro modo a rota responde 404.
 * O link de pagamento do fake aponta para estes endpoints.
 */
@RestController
@RequestMapping("/public/fake-pagamentos")
@ConditionalOnProperty(name = "billing.mercadopago.modo", havingValue = "fake", matchIfMissing = true)
@Tag(name = "Pagamento fake (demo)")
public class FakePagamentoController {

    private final ProcessarResultadoPagamentoUseCase processar;
    private final FakePagamentoGateway fake;
    private final TransactionTemplate tx;

    public FakePagamentoController(ProcessarResultadoPagamentoUseCase processar, FakePagamentoGateway fake,
            TransactionTemplate tx) {
        this.processar = processar;
        this.fake = fake;
        this.tx = tx;
    }

    @PostMapping("/{pagamentoId}/aprovar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Simula pagamento aprovado")
    public void aprovar(@PathVariable UUID pagamentoId) {
        aplicar(pagamentoId, SituacaoPagamentoExterno.APROVADO);
    }

    @PostMapping("/{pagamentoId}/rejeitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Simula pagamento rejeitado")
    public void rejeitar(@PathVariable UUID pagamentoId) {
        aplicar(pagamentoId, SituacaoPagamentoExterno.REJEITADO);
    }

    private void aplicar(UUID pagamentoId, SituacaoPagamentoExterno situacao) {
        ResultadoProcessamento resultado = tx.execute(
                status -> processar.execute(pagamentoId, FakePagamentoGateway.mpPaymentIdDe(pagamentoId), situacao));
        if (resultado == ResultadoProcessamento.DESCONHECIDO) {
            throw new RecursoNaoEncontradoException("Pagamento", pagamentoId);
        }
        fake.registrarSituacao(pagamentoId, situacao);
    }
}
