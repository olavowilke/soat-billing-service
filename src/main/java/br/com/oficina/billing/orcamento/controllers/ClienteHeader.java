package br.com.oficina.billing.orcamento.controllers;

import java.util.Optional;
import java.util.UUID;

/** Le o cabecalho x-cliente-id que o gateway da API injeta. Ausente ou em branco: chamada da equipe. */
final class ClienteHeader {

    static final String NOME = "x-cliente-id";

    private ClienteHeader() {
    }

    static Optional<UUID> ler(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(valor.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cabecalho " + NOME + " invalido");
        }
    }
}
