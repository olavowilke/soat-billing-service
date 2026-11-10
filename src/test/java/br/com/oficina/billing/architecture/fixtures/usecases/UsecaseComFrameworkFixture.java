package br.com.oficina.billing.architecture.fixtures.usecases;

/** Fixture: use case that breaks the framework rule and the adapter rule. */
public class UsecaseComFrameworkFixture {
    org.springframework.http.HttpStatus status;
    br.com.oficina.billing.architecture.fixtures.infrastructure.AdapterFixture adapter;
}
