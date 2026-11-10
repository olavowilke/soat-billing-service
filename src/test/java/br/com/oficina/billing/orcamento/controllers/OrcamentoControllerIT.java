package br.com.oficina.billing.orcamento.controllers;

import br.com.oficina.billing.orcamento.entities.ItemOrcamento;
import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.entities.StatusOrcamento;
import br.com.oficina.billing.orcamento.entities.StatusPagamento;
import br.com.oficina.billing.orcamento.entities.TipoItem;
import br.com.oficina.billing.orcamento.gateways.ProvedorPagamentoIndisponivelException;
import br.com.oficina.billing.orcamento.gateways.SituacaoPagamentoExterno;
import br.com.oficina.billing.orcamento.infrastructure.fake.FakePagamentoGateway;
import br.com.oficina.billing.orcamento.presenters.OrcamentoResponse;
import br.com.oficina.billing.shared.BillingIT;
import br.com.oficina.billing.shared.presenters.ApiError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@ExtendWith(OutputCaptureExtension.class)
class OrcamentoControllerIT extends BillingIT {

    private static final UUID CLIENTE = UUID.randomUUID();

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private FakePagamentoGateway fake;

    @BeforeEach
    @AfterEach
    void limpar() {
        jdbc.update("delete from outbox");
    }

    private Orcamento gerado() {
        return orcamentoGateway.salvar(Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), "corr-" + UUID.randomUUID(), CLIENTE, List.of(
                new ItemOrcamento(TipoItem.SERVICO, UUID.randomUUID(), "Troca de oleo", 1,
                        new BigDecimal("350.00"))), Instant.now()));
    }

    private Orcamento aprovado() {
        Orcamento o = gerado();
        o.aprovar(Instant.now(), java.time.Duration.ofMinutes(30)).definirCobranca("pref", "https://pagar");
        return orcamentoGateway.salvar(o);
    }

    private HttpEntity<Void> cabecalho(String cliente) {
        HttpHeaders h = new HttpHeaders();
        if (cliente != null) {
            h.add("x-cliente-id", cliente);
        }
        return new HttpEntity<>(h);
    }

    private <T> ResponseEntity<T> chamar(HttpMethod metodo, String url, String cliente, Class<T> tipo) {
        return http.exchange(url, metodo, cabecalho(cliente), tipo);
    }

    private List<JsonNode> outbox(UUID sagaId, String tipo) throws Exception {
        List<String> payloads = jdbc.queryForList("select payload::text from outbox where saga_id = ? and tipo = ?",
                String.class, sagaId, tipo);
        return payloads.stream().map(p -> {
            try {
                return mapper.readTree(p);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    private int linhasOutbox(UUID sagaId) {
        return jdbc.queryForObject("select count(*) from outbox where saga_id = ?", Integer.class, sagaId);
    }

    @Test
    void aprovarRetornaLinkEPublicaAprovado() throws Exception {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", CLIENTE.toString(),
                OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrcamentoResponse corpo = resposta.getBody();
        assertThat(corpo.status()).isEqualTo("APROVADO");
        assertThat(corpo.linkPagamento()).contains("/api/billing/public/fake-pagamentos/");
        assertThat(corpo.pagamento().status()).isEqualTo("PENDENTE");
        var linhas = outbox(o.getSagaId(), "OrcamentoAprovado");
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("pagamentoId").asText()).isEqualTo(corpo.pagamento().id().toString());
        assertThat(linhas.get(0).get("linkPagamento").asText()).isEqualTo(corpo.linkPagamento());
        assertThat(orcamentoGateway.buscarPorId(o.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.APROVADO);
    }

    @Test
    void aprovarComOutroCorrelationIdHttpAindaRespondeComOIdDaSaga() {
        Orcamento o = gerado();
        HttpHeaders h = new HttpHeaders();
        h.add("x-correlation-id", "id-da-requisicao-http");

        var resposta = http.exchange("/orcamentos/" + o.getId() + "/aprovar", HttpMethod.POST, new HttpEntity<>(h),
                OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class,
                o.getSagaId())).isEqualTo(o.getCorrelationId()).isNotEqualTo("id-da-requisicao-http");
    }

    @Test
    void recusarComOutroCorrelationIdHttpAindaRespondeComOIdDaSaga() {
        Orcamento o = gerado();
        HttpHeaders h = new HttpHeaders();
        h.add("x-correlation-id", "id-da-requisicao-http");

        http.exchange("/orcamentos/" + o.getId() + "/recusar", HttpMethod.POST, new HttpEntity<>(h),
                OrcamentoResponse.class);

        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class,
                o.getSagaId())).isEqualTo(o.getCorrelationId());
    }

    @Test
    void fakeAprovarRespondeComOIdDaSagaEmVezDoDaRequisicao() {
        Orcamento o = aprovado();
        HttpHeaders h = new HttpHeaders();
        h.add("x-correlation-id", "id-da-requisicao-http");

        http.exchange("/public/fake-pagamentos/" + o.getPagamento().orElseThrow().getId() + "/aprovar",
                HttpMethod.POST, new HttpEntity<>(h), Void.class);

        assertThat(jdbc.queryForObject("select correlation_id from outbox where saga_id = ?", String.class,
                o.getSagaId())).isEqualTo(o.getCorrelationId());
    }

    @Test
    void outroClienteRecebe403() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", UUID.randomUUID().toString(),
                ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().status()).isEqualTo(403);
        assertThat(orcamentoGateway.buscarPorId(o.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.GERADO);
        assertThat(linhasOutbox(o.getSagaId())).isZero();
    }

    @Test
    void semCabecalhoClientePermite() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", null, OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cabecalhoEmBrancoTrataComoAusente() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/recusar", " ", OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cabecalhoMalformadoRetorna400() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", "nao-e-uuid", ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orcamentoGateway.buscarPorId(o.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.GERADO);
    }

    @Test
    void aprovarJaAprovadoRetorna409SemSegundaLinha() throws Exception {
        Orcamento o = gerado();
        chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", null, OrcamentoResponse.class);

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outbox(o.getSagaId(), "OrcamentoAprovado")).hasSize(1);
    }

    @Test
    void recusarPublicaRecusado() throws Exception {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/recusar", CLIENTE.toString(),
                OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().status()).isEqualTo("RECUSADO");
        var linhas = outbox(o.getSagaId(), "OrcamentoRecusado");
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("orcamentoId").asText()).isEqualTo(o.getId().toString());
    }

    @Test
    void recusarDepoisDeAprovarRetorna409() {
        Orcamento o = aprovado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/recusar", null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recusarOutroClienteRetorna403() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/recusar", UUID.randomUUID().toString(),
                ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void orcamentoDesconhecidoRetorna404() {
        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + UUID.randomUUID() + "/aprovar", null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void falhaDoProvedorRetorna502ENaoMudaEstado(CapturedOutput saida) {
        Orcamento o = gerado();
        doThrow(new ProvedorPagamentoIndisponivelException("provedor fora do ar"))
                .when(pagamentoGateway).criarCobranca(any(), anyString(), any());

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/aprovar", null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resposta.getBody().status()).isEqualTo(502);
        Orcamento depois = orcamentoGateway.buscarPorId(o.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(depois.getPagamento()).isEmpty();
        assertThat(linhasOutbox(o.getSagaId())).isZero();
        assertThat(saida.getOut()).contains("WARN").contains("provedor fora do ar").doesNotContain("ERROR");
    }

    @Test
    void conflitoOtimistaNaGravacaoRetorna409(CapturedOutput saida) {
        Orcamento o = gerado();
        doThrow(new ObjectOptimisticLockingFailureException(Orcamento.class, o.getId()))
                .when(orcamentoGateway).salvar(any());

        var resposta = chamar(HttpMethod.POST, "/orcamentos/" + o.getId() + "/recusar", null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(saida.getOut()).contains("WARN").contains("Conflito de gravacao concorrente");
        assertThat(linhasOutbox(o.getSagaId())).isZero();
    }

    @Test
    void buscarPorOsRetornaOrcamentoParaODono() {
        Orcamento o = aprovado();

        var resposta = chamar(HttpMethod.GET, "/orcamentos/os/" + o.getOsId(), CLIENTE.toString(),
                OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrcamentoResponse corpo = resposta.getBody();
        assertThat(corpo.id()).isEqualTo(o.getId());
        assertThat(corpo.osId()).isEqualTo(o.getOsId());
        assertThat(corpo.valorTotal()).isEqualByComparingTo("350.00");
        assertThat(corpo.itens()).hasSize(1);
        assertThat(corpo.itens().get(0).subtotal()).isEqualByComparingTo("350.00");
        assertThat(corpo.linkPagamento()).isEqualTo("https://pagar");
        assertThat(corpo.pagamento().status()).isEqualTo("PENDENTE");
    }

    @Test
    void buscarPorOsSemPagamentoNaoTemLink() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.GET, "/orcamentos/os/" + o.getOsId(), null, OrcamentoResponse.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().pagamento()).isNull();
        assertThat(resposta.getBody().linkPagamento()).isNull();
    }

    @Test
    void buscarPorOsDeOutroClienteRetorna403() {
        Orcamento o = gerado();

        var resposta = chamar(HttpMethod.GET, "/orcamentos/os/" + o.getOsId(), UUID.randomUUID().toString(),
                ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buscarPorOsDesconhecidaRetorna404() {
        var resposta = chamar(HttpMethod.GET, "/orcamentos/os/" + UUID.randomUUID(), null, ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fakeAprovarConfirmaPagamentoERegistraSituacao() throws Exception {
        Orcamento o = aprovado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();

        var resposta = chamar(HttpMethod.POST, "/public/fake-pagamentos/" + pagamentoId + "/aprovar", null,
                Void.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Orcamento depois = orcamentoGateway.buscarPorId(o.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        assertThat(depois.getPagamento().orElseThrow().getMpPaymentId()).isEqualTo("fake-" + pagamentoId);
        assertThat(outbox(o.getSagaId(), "PagamentoConfirmado")).hasSize(1);
        assertThat(fake.consultar("fake-" + pagamentoId).situacao()).isEqualTo(SituacaoPagamentoExterno.APROVADO);
    }

    @Test
    void fakeRejeitarCancelaEPublicaFalhou() throws Exception {
        Orcamento o = aprovado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();

        var resposta = chamar(HttpMethod.POST, "/public/fake-pagamentos/" + pagamentoId + "/rejeitar", null,
                Void.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Orcamento depois = orcamentoGateway.buscarPorId(o.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(depois.getPagamento().orElseThrow().getStatus()).isEqualTo(StatusPagamento.REJEITADO);
        var linhas = outbox(o.getSagaId(), "PagamentoFalhou");
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).get("motivo").asText()).isEqualTo("REJEITADO");
        assertThat(fake.consultar("fake-" + pagamentoId).situacao()).isEqualTo(SituacaoPagamentoExterno.REJEITADO);
    }

    @Test
    void fakeAprovarDuasVezesNaoPublicaDeNovo() throws Exception {
        Orcamento o = aprovado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();
        chamar(HttpMethod.POST, "/public/fake-pagamentos/" + pagamentoId + "/aprovar", null, Void.class);

        var resposta = chamar(HttpMethod.POST, "/public/fake-pagamentos/" + pagamentoId + "/aprovar", null,
                Void.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(outbox(o.getSagaId(), "PagamentoConfirmado")).hasSize(1);
    }

    @Test
    void fakePaginaDePagamentoMostraValorStatusEFormularios() {
        Orcamento o = aprovado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();

        var resposta = chamar(HttpMethod.GET, "/public/fake-pagamentos/" + pagamentoId, null, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(resposta.getBody()).contains("PENDENTE").contains("350.00")
                .contains("method=\"post\"").contains("action=\"" + pagamentoId + "/aprovar\"")
                .contains("action=\"" + pagamentoId + "/rejeitar\"");
    }

    @Test
    void fakePaginaDePagamentoDesconhecidoRetorna404() {
        var resposta = chamar(HttpMethod.GET, "/public/fake-pagamentos/" + UUID.randomUUID(), null, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fakePagamentoDesconhecidoRetorna404ComWarn(CapturedOutput saida) {
        UUID desconhecido = UUID.randomUUID();

        var resposta = chamar(HttpMethod.POST, "/public/fake-pagamentos/" + desconhecido + "/aprovar", null,
                ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(saida.getOut()).contains("WARN").contains(desconhecido.toString());
    }

    @Test
    void fakeComConflitoOtimistaRetorna409(CapturedOutput saida) {
        Orcamento o = aprovado();
        UUID pagamentoId = o.getPagamento().orElseThrow().getId();
        doThrow(new ObjectOptimisticLockingFailureException(Orcamento.class, o.getId()))
                .when(orcamentoGateway).salvar(any());

        var resposta = chamar(HttpMethod.POST, "/public/fake-pagamentos/" + pagamentoId + "/aprovar", null,
                ApiError.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(saida.getOut()).contains("WARN").contains("Conflito de gravacao concorrente");
    }
}
