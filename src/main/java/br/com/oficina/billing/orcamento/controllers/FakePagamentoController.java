package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.infrastructure.fake.FakePagamentoGateway;
import br.com.oficina.billing.orcamento.usecases.BuscarPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
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
    private final BuscarPagamentoUseCase buscar;
    private final FakePagamentoGateway fake;
    private final TransactionTemplate tx;

    public FakePagamentoController(ProcessarResultadoPagamentoUseCase processar, BuscarPagamentoUseCase buscar,
            FakePagamentoGateway fake, TransactionTemplate tx) {
        this.processar = processar;
        this.buscar = buscar;
        this.fake = fake;
        this.tx = tx;
    }

    @GetMapping("/{pagamentoId}")
    @Operation(summary = "Pagina de pagamento fake com os botoes de aprovar e rejeitar")
    public ResponseEntity<String> pagina(@PathVariable UUID pagamentoId) {
        return buscar.execute(pagamentoId)
                .map(p -> html(HttpStatus.OK, htmlDoPagamento(p)))
                .orElseGet(() -> html(HttpStatus.NOT_FOUND,
                        htmlBase("Pagamento nao encontrado", "<p>Pagamento nao encontrado.</p>")));
    }

    private static ResponseEntity<String> html(HttpStatus status, String corpo) {
        return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(corpo);
    }

    // Os valores inseridos (UUID, enum, numero) nao carregam caracteres de HTML. As acoes sao relativas
    // para funcionar atras do gateway, seja qual for o prefixo da rota.
    private static String htmlDoPagamento(Pagamento p) {
        String corpo = "<p>Valor: R$ " + p.getValor().toPlainString() + "</p>"
                + "<p>Situacao: " + p.getStatus() + "</p>"
                + "<form method=\"post\" action=\"" + p.getId() + "/aprovar\">"
                + "<button type=\"submit\">Aprovar pagamento</button></form>"
                + "<form method=\"post\" action=\"" + p.getId() + "/rejeitar\">"
                + "<button type=\"submit\">Rejeitar pagamento</button></form>";
        return htmlBase("Pagamento de demonstracao", corpo);
    }

    private static String htmlBase(String titulo, String corpo) {
        return "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><title>" + titulo
                + "</title></head><body><h1>" + titulo + "</h1>" + corpo + "</body></html>";
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
