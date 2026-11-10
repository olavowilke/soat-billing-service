package br.com.oficina.billing.shared.entities;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }

    public RecursoNaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado(a) com id: " + id);
    }
}
