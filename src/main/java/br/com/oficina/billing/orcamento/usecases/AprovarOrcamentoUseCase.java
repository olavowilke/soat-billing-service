package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.Pagamento;
import br.com.oficina.billing.orcamento.gateways.CobrancaCriada;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.RespostaSagaGateway;
import br.com.oficina.billing.shared.entities.RecursoNaoEncontradoException;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * O cliente aprova o orcamento: cria o pagamento PENDENTE, pede a cobranca ao provedor, grava e responde
 * OrcamentoAprovado. Se o provedor falhar, a excecao sobe e o chamador desfaz a transacao inteira.
 */
public class AprovarOrcamentoUseCase {

    private final OrcamentoGateway orcamentos;
    private final PagamentoGateway provedor;
    private final RespostaSagaGateway respostas;
    private final Clock clock;
    private final Duration prazoPagamento;

    public AprovarOrcamentoUseCase(OrcamentoGateway orcamentos, PagamentoGateway provedor,
            RespostaSagaGateway respostas, Clock clock, Duration prazoPagamento) {
        this.orcamentos = orcamentos;
        this.provedor = provedor;
        this.respostas = respostas;
        this.clock = clock;
        this.prazoPagamento = prazoPagamento;
    }

    public Orcamento execute(UUID orcamentoId, Optional<UUID> clienteId) {
        Orcamento orcamento = orcamentos.buscarPorId(orcamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento", orcamentoId));
        ClienteNaoAutorizadoException.exigirDono(orcamento, clienteId);

        Pagamento pagamento = orcamento.aprovar(clock.instant(), prazoPagamento);
        CobrancaCriada cobranca = provedor.criarCobranca(pagamento.getId(),
                "Orcamento da OS " + orcamento.getOsId(), pagamento.getValor());
        pagamento.definirCobranca(cobranca.preferenceId(), cobranca.linkPagamento());

        Orcamento salvo = orcamentos.salvar(orcamento);
        respostas.orcamentoAprovado(salvo.getSagaId(), salvo.getCorrelationId(), salvo.getOsId(), salvo.getId(), pagamento.getId(),
                cobranca.linkPagamento());
        return salvo;
    }
}
