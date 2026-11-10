package br.com.oficina.billing.architecture.fixtures.gateways;

/** Fixture: gateway that breaks the framework rule and the adapter rule. */
public class GatewayComFrameworkFixture {
    org.springframework.http.HttpStatus status;
    br.com.oficina.billing.architecture.fixtures.infrastructure.AdapterFixture adapter;
}
