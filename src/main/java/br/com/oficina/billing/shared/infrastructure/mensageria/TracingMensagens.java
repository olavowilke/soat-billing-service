package br.com.oficina.billing.shared.infrastructure.mensageria;

import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TransportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Liga o trace distribuido do New Relic aos atributos das mensagens SQS.
 * Sem o agente, a API do New Relic nao faz nada e estes metodos devolvem vazio ou ignoram a chamada.
 */
public final class TracingMensagens {

    public static final String ATRIBUTO_NEWRELIC = "newrelic";

    private static final Logger log = LoggerFactory.getLogger(TracingMensagens.class);

    private TracingMensagens() {
    }

    /** Atributos de trace para uma mensagem que sai. Vazio se nao houver transacao ou agente. */
    public static Map<String, String> cabecalhosSaida() {
        try {
            Headers cabecalhos = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
            NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(cabecalhos);
            var valor = cabecalhos.getHeader(ATRIBUTO_NEWRELIC);
            return valor == null || valor.isBlank() ? Map.of() : Map.of(ATRIBUTO_NEWRELIC, valor);
        } catch (RuntimeException e) {
            log.debug("Trace distribuido indisponivel para mensagem de saida", e);
            return Map.of();
        }
    }

    /** Aceita o trace de uma mensagem que chegou, com TransportType.Other (newrelic-api 8.16.0 nao tem TransportType.Queue). */
    public static void aceitar(Map<String, String> atributos) {
        if (atributos == null || atributos.get(ATRIBUTO_NEWRELIC) == null) {
            return;
        }
        try {
            Headers cabecalhos = ConcurrentHashMapHeaders.buildFromFlatMap(HeaderType.MESSAGE,
                    new HashMap<>(Map.of(ATRIBUTO_NEWRELIC, atributos.get(ATRIBUTO_NEWRELIC))));
            NewRelic.getAgent().getTransaction().acceptDistributedTraceHeaders(TransportType.Other, cabecalhos);
        } catch (RuntimeException e) {
            log.debug("Trace distribuido nao aceito", e);
        }
    }
}
