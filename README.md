# Billing Service

Microservice for budgets (orcamentos), Mercado Pago payments and refunds.
It has its own Postgres database and talks to the OS Saga over SQS.
Mercado Pago runs in sandbox only. Set `BILLING_MERCADOPAGO_MODO` to `fake` (default) or `real`.
With `real`, you must set `BILLING_MERCADOPAGO_ACCESS_TOKEN` and `BILLING_MERCADOPAGO_WEBHOOK_SECRET`. Without them the app will not start.

## Run the tests

You need Java 21 and Docker. The integration tests start Postgres and LocalStack.

```
./mvnw verify
```

JaCoCo fails the build if line coverage drops under 80%.
