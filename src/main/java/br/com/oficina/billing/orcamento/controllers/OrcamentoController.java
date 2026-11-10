package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.presenters.OrcamentoPresenter;
import br.com.oficina.billing.orcamento.presenters.OrcamentoResponse;
import br.com.oficina.billing.orcamento.usecases.AprovarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.BuscarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.RecusarOrcamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API do cliente. O gateway da API manda x-cliente-id. Sem o cabecalho a chamada veio da equipe (port-forward).
 * Cada escrita roda em uma transacao, porque a resposta da saga vai para a outbox na mesma transacao.
 */
@RestController
@RequestMapping("/orcamentos")
@Tag(name = "Orcamento (cliente)")
public class OrcamentoController {

    private final AprovarOrcamentoUseCase aprovar;
    private final RecusarOrcamentoUseCase recusar;
    private final BuscarOrcamentoUseCase buscar;
    private final TransactionTemplate tx;

    public OrcamentoController(AprovarOrcamentoUseCase aprovar, RecusarOrcamentoUseCase recusar,
            BuscarOrcamentoUseCase buscar, TransactionTemplate tx) {
        this.aprovar = aprovar;
        this.recusar = recusar;
        this.buscar = buscar;
        this.tx = tx;
    }

    @GetMapping("/os/{osId}")
    @Operation(summary = "Busca o orcamento de uma OS")
    public OrcamentoResponse buscarPorOs(@PathVariable UUID osId,
            @RequestHeader(name = ClienteHeader.NOME, required = false) String clienteId) {
        return OrcamentoPresenter.toResponse(buscar.execute(osId, ClienteHeader.ler(clienteId)));
    }

    @PostMapping("/{id}/aprovar")
    @Operation(summary = "Aprova o orcamento e devolve o link de pagamento")
    public OrcamentoResponse aprovar(@PathVariable UUID id,
            @RequestHeader(name = ClienteHeader.NOME, required = false) String clienteId) {
        var cliente = ClienteHeader.ler(clienteId);
        return tx.execute(status -> OrcamentoPresenter.toResponse(aprovar.execute(id, cliente)));
    }

    @PostMapping("/{id}/recusar")
    @Operation(summary = "Recusa o orcamento")
    public OrcamentoResponse recusar(@PathVariable UUID id,
            @RequestHeader(name = ClienteHeader.NOME, required = false) String clienteId) {
        var cliente = ClienteHeader.ler(clienteId);
        return tx.execute(status -> OrcamentoPresenter.toResponse(recusar.execute(id, cliente)));
    }
}
