package br.com.oficina.billing.orcamento.infrastructure.mercadopago;

import br.com.oficina.billing.orcamento.gateways.CobrancaCriada;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.ProvedorPagamentoIndisponivelException;
import br.com.oficina.billing.orcamento.gateways.ResultadoConsultaPagamento;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Mercado Pago de verdade (so sandbox), ativo com billing.mercadopago.modo=real.
 * Erro HTTP, timeout ou resposta sem os campos esperados viram {@link ProvedorPagamentoIndisponivelException}
 * (a API responde 502).
 */
@Component
@ConditionalOnProperty(name = "billing.mercadopago.modo", havingValue = "real")
public class MercadoPagoPagamentoGateway implements PagamentoGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPagamentoGateway.class);

    private final RestClient http;
    private final boolean sandbox;
    private final String notificationUrl;

    public MercadoPagoPagamentoGateway(MercadoPagoProperties props,
            @Value("${billing.url-publica}") String urlPublica) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(props.connectTimeout());
        fabrica.setReadTimeout(props.readTimeout());
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + props.accessToken())
                .build();
        this.sandbox = props.sandbox();
        String base = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        this.notificationUrl = base + "/api/billing/public/webhooks/mercadopago";
    }

    @Override
    public CobrancaCriada criarCobranca(UUID pagamentoId, String descricao, BigDecimal valor) {
        Map<String, Object> corpo = Map.of(
                "items", List.of(Map.of("title", descricao, "quantity", 1, "unit_price", valor,
                        "currency_id", "BRL")),
                "external_reference", pagamentoId.toString(),
                "notification_url", notificationUrl);
        JsonNode resposta = chamar("criar cobranca", () -> http.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class));
        String link = texto(resposta, sandbox ? "sandbox_init_point" : "init_point");
        String preferenceId = texto(resposta, "id");
        if (link == null || preferenceId == null) {
            throw falha("criar cobranca", "resposta sem id ou link de pagamento", null);
        }
        return new CobrancaCriada(preferenceId, link);
    }

    @Override
    public ResultadoConsultaPagamento consultar(String mpPaymentId) {
        JsonNode resposta = chamar("consultar pagamento", () -> http.get()
                .uri("/v1/payments/{id}", mpPaymentId).retrieve().body(JsonNode.class));
        return new ResultadoConsultaPagamento(lerReferencia(resposta), mpPaymentId,
                situacao(texto(resposta, "status")));
    }

    @Override
    public void estornar(String mpPaymentId, UUID pagamentoId) {
        chamar("estornar pagamento", () -> http.post().uri("/v1/payments/{id}/refunds", mpPaymentId)
                .header("X-Idempotency-Key", pagamentoId + "-" + mpPaymentId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().toBodilessEntity());
    }

    private <T> T chamar(String operacao, Supplier<T> chamada) {
        T resultado;
        try {
            resultado = chamada.get();
        } catch (RestClientException e) {
            throw falha(operacao, e.getMessage(), e);
        }
        if (resultado == null) {
            throw falha(operacao, "resposta vazia", null);
        }
        return resultado;
    }

    private static ProvedorPagamentoIndisponivelException falha(String operacao, String causa, Throwable erro) {
        String mensagem = "Mercado Pago: falha ao " + operacao + ": " + causa;
        log.warn(mensagem);
        return erro == null ? new ProvedorPagamentoIndisponivelException(mensagem)
                : new ProvedorPagamentoIndisponivelException(mensagem, erro);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        return valor == null || valor.isNull() || valor.asText().isBlank() ? null : valor.asText();
    }

    /** Nulo quando a referencia nao e um UUID: o pagamento nao e nosso. */
    private static UUID lerReferencia(JsonNode resposta) {
        String referencia = texto(resposta, "external_reference");
        try {
            return referencia == null ? null : UUID.fromString(referencia);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SituacaoPagamentoExterno situacao(String status) {
        if (status == null) {
            return SituacaoPagamentoExterno.PENDENTE;
        }
        return switch (status) {
            case "approved" -> SituacaoPagamentoExterno.APROVADO;
            case "rejected", "cancelled" -> SituacaoPagamentoExterno.REJEITADO;
            default -> SituacaoPagamentoExterno.PENDENTE;
        };
    }
}
