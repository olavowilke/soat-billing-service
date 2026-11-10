package br.com.oficina.billing.orcamento.usecases;

import br.com.oficina.billing.orcamento.entities.Orcamento;

import java.util.Optional;
import java.util.UUID;

/** O cliente do cabecalho nao e o dono do orcamento. A API responde 403. */
public class ClienteNaoAutorizadoException extends RuntimeException {

    public ClienteNaoAutorizadoException(UUID orcamentoId) {
        super("Orcamento " + orcamentoId + " nao pertence ao cliente informado");
    }

    /** Sem cabecalho a chamada veio da equipe (port-forward), entao passa. Com cabecalho, tem de ser o dono. */
    static void exigirDono(Orcamento orcamento, Optional<UUID> clienteId) {
        if (clienteId.isPresent() && !orcamento.pertenceAo(clienteId.get())) {
            throw new ClienteNaoAutorizadoException(orcamento.getId());
        }
    }
}
