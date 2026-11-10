package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.gateways.PagamentoGateway;
import br.com.oficina.billing.orcamento.gateways.ProvedorPagamentoIndisponivelException;
import br.com.oficina.billing.orcamento.gateways.ResultadoConsultaPagamento;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.usecases.ProcessarResultadoPagamentoUseCase;
import br.com.oficina.billing.orcamento.usecases.ResultadoProcessamento;
import br.com.oficina.billing.shared.infrastructure.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MercadoPagoWebhookController.class, properties = {
        "billing.mercadopago.modo=real", "billing.mercadopago.webhook-secret=segredo-teste"})
@Import(GlobalExceptionHandler.class)
@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoWebhookControllerTest {

    private static final String SEGREDO = "segredo-teste";
    private static final String REQUEST_ID = "req-1";
    private static final UUID PAGAMENTO = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ProcessarResultadoPagamentoUseCase processar;
    @MockitoBean
    private PagamentoGateway provedor;
    @MockitoBean
    private TransactionTemplate tx;

    @BeforeEach
    void transacaoDeMentira() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private static String assinatura(String dataId, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifesto = "id:" + dataId.toLowerCase() + ";request-id:" + REQUEST_ID + ";ts:1700000000;";
        return "ts=1700000000,v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    private org.springframework.test.web.servlet.ResultActions chamarAssinado(String tipo, String dataId)
            throws Exception {
        return mvc.perform(post("/public/webhooks/mercadopago").param("type", tipo).param("data.id", dataId)
                .header("x-signature", assinatura(dataId, SEGREDO)).header("x-request-id", REQUEST_ID));
    }

    @Test
    void assinaturaInvalidaRetorna401(CapturedOutput saida) throws Exception {
        mvc.perform(post("/public/webhooks/mercadopago").param("type", "payment").param("data.id", "123")
                .header("x-signature", assinatura("123", "segredo-errado")).header("x-request-id", REQUEST_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provedor, processar);
        assertThat(saida.getOut()).contains("WARN").contains("assinatura").doesNotContain(SEGREDO);
    }

    @Test
    void semCabecalhosRetorna401(CapturedOutput saida) throws Exception {
        mvc.perform(post("/public/webhooks/mercadopago").param("type", "payment").param("data.id", "123"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provedor, processar);
        assertThat(saida.getOut()).contains("WARN").contains("assinatura invalida");
    }

    @Test
    void semDataIdRetorna401(CapturedOutput saida) throws Exception {
        mvc.perform(post("/public/webhooks/mercadopago").param("type", "payment")
                .header("x-signature", assinatura("123", SEGREDO)).header("x-request-id", REQUEST_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provedor, processar);
        assertThat(saida.getOut()).contains("WARN").contains("assinatura invalida");
    }

    @Test
    void tipoDiferenteDePaymentRetorna200SemConsultar() throws Exception {
        chamarAssinado("merchant_order", "55").andExpect(status().isOk());

        verifyNoInteractions(provedor, processar);
    }

    @Test
    void pagamentoAprovadoConsultaAntesDaTransacaoEProcessa() throws Exception {
        when(provedor.consultar("123")).thenReturn(
                new ResultadoConsultaPagamento(PAGAMENTO, "123", SituacaoPagamentoExterno.APROVADO));
        when(processar.execute(PAGAMENTO, "123", SituacaoPagamentoExterno.APROVADO))
                .thenReturn(ResultadoProcessamento.APLICADO);

        chamarAssinado("payment", "123").andExpect(status().isOk());

        InOrder ordem = inOrder(provedor, tx, processar);
        ordem.verify(provedor).consultar("123");
        ordem.verify(tx).execute(any());
        ordem.verify(processar).execute(PAGAMENTO, "123", SituacaoPagamentoExterno.APROVADO);
    }

    @Test
    void referenciaDesconhecidaRetorna200() throws Exception {
        when(provedor.consultar("124")).thenReturn(
                new ResultadoConsultaPagamento(PAGAMENTO, "124", SituacaoPagamentoExterno.APROVADO));
        when(processar.execute(PAGAMENTO, "124", SituacaoPagamentoExterno.APROVADO))
                .thenReturn(ResultadoProcessamento.DESCONHECIDO);

        chamarAssinado("payment", "124").andExpect(status().isOk());
    }

    @Test
    void pagamentoSemReferenciaNossaRetorna200SemProcessar(CapturedOutput saida) throws Exception {
        when(provedor.consultar("125")).thenReturn(
                new ResultadoConsultaPagamento(null, "125", SituacaoPagamentoExterno.APROVADO));

        chamarAssinado("payment", "125").andExpect(status().isOk());

        verify(processar, never()).execute(any(), anyString(), any());
        assertThat(saida.getOut()).contains("WARN").contains("125");
    }

    @Test
    void falhaDoProvedorNaConsultaRetorna502ParaOMercadoPagoTentarDeNovo(CapturedOutput saida) throws Exception {
        when(provedor.consultar("126")).thenThrow(new ProvedorPagamentoIndisponivelException("fora do ar"));

        chamarAssinado("payment", "126").andExpect(status().isBadGateway());

        verifyNoInteractions(processar);
        assertThat(saida.getOut()).contains("WARN").contains("fora do ar");
    }
}
