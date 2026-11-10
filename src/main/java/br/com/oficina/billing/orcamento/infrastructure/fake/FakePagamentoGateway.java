package br.com.oficina.billing.orcamento.infrastructure.fake;

import br.com.oficina.billing.orcamento.gateways.CobrancaCriada;
import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.ResultadoConsultaPagamento;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provedor de pagamento falso, ativo com billing.mercadopago.modo=fake (o padrao).
 * O link aponta para os endpoints fake do proprio Billing. Esses endpoints gravam a situacao aqui
 * com {@link #registrarSituacao}, e {@link #consultar} a devolve. O mpPaymentId do fake e "fake-{pagamentoId}".
 */
@Component
@ConditionalOnProperty(name = "billing.mercadopago.modo", havingValue = "fake", matchIfMissing = true)
public class FakePagamentoGateway implements PagamentoGateway {

    private static final String PREFIXO = "fake-";

    private final String urlPublica;
    private final boolean falharEstorno;
    private final Map<UUID, SituacaoPagamentoExterno> situacoes = new ConcurrentHashMap<>();

    public FakePagamentoGateway(@Value("${billing.url-publica}") String urlPublica,
            @Value("${billing.fake.falhar-estorno:false}") boolean falharEstorno) {
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        this.falharEstorno = falharEstorno;
    }

    public static String mpPaymentIdDe(UUID pagamentoId) {
        return PREFIXO + pagamentoId;
    }

    /** Usado pelos endpoints fake para dizer como o "provedor" ve o pagamento. */
    public void registrarSituacao(UUID pagamentoId, SituacaoPagamentoExterno situacao) {
        situacoes.put(pagamentoId, situacao);
    }

    @Override
    public CobrancaCriada criarCobranca(UUID pagamentoId, String descricao, BigDecimal valor) {
        return new CobrancaCriada("fake-pref-" + pagamentoId,
                urlPublica + "/api/billing/public/fake-pagamentos/" + pagamentoId);
    }

    @Override
    public ResultadoConsultaPagamento consultar(String mpPaymentId) {
        if (mpPaymentId == null || !mpPaymentId.startsWith(PREFIXO)) {
            throw new IllegalArgumentException("Identificador nao e do provedor fake: " + mpPaymentId);
        }
        UUID pagamentoId = UUID.fromString(mpPaymentId.substring(PREFIXO.length()));
        return new ResultadoConsultaPagamento(pagamentoId, mpPaymentId,
                situacoes.getOrDefault(pagamentoId, SituacaoPagamentoExterno.PENDENTE));
    }

    @Override
    public void estornar(String mpPaymentId, UUID pagamentoId) {
        if (falharEstorno) {
            throw new IllegalStateException("Estorno recusado pelo provedor fake (billing.fake.falhar-estorno)");
        }
    }
}
