# Billing Service

Microservice for budgets (orcamentos), Mercado Pago payments and refunds.
It has its own Postgres database and talks to the OS Saga over SQS.
Mercado Pago runs in sandbox only. Set `BILLING_MERCADOPAGO_MODO` to `fake` (default) or `real`.

## Run the tests

You need Java 21 and Docker. The integration tests start Postgres and LocalStack.

```
./mvnw verify
```

JaCoCo fails the build if line coverage drops under 80%.
