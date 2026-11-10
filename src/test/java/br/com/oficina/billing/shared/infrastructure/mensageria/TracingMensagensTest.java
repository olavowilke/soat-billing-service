package br.com.oficina.billing.shared.infrastructure.mensageria;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TracingMensagensTest {

    @Test
    void semAgenteNaoGeraCabecalhoNewRelic() {
        assertThat(TracingMensagens.cabecalhosSaida()).isEmpty();
    }

    @Test
    void semAgenteAceitarNaoFalha() {
        assertThatCode(() -> TracingMensagens.aceitar(Map.of("newrelic", "abc"))).doesNotThrowAnyException();
        assertThatCode(() -> TracingMensagens.aceitar(Map.of())).doesNotThrowAnyException();
        assertThatCode(() -> TracingMensagens.aceitar(null)).doesNotThrowAnyException();
    }
}
