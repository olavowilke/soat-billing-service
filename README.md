# Billing Service

[![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=olavowilke_soat-billing-service&metric=coverage)](https://sonarcloud.io/project/overview?id=olavowilke_soat-billing-service)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=olavowilke_soat-billing-service&metric=alert_status)](https://sonarcloud.io/project/overview?id=olavowilke_soat-billing-service)

Microsserviço de orçamento, pagamento e estorno da oficina (Tech Challenge FIAP, Fase 4).
Ele é um dos três serviços da Saga da OS. O OS Service orquestra a Saga e este serviço responde pelos passos de dinheiro.

## Propósito

- Gera o orçamento de uma OS quando o OS Service pede (`GerarOrcamento`).
- Deixa o cliente aprovar ou recusar o orçamento.
- Cria o pagamento no Mercado Pago (sandbox) e confirma o resultado pelo webhook.
- Cancela o orçamento quando o pagamento falha ou expira.
- Estorna o pagamento quando o OS Service pede (`EstornarPagamento`).

A arquitetura completa, a escolha da Saga e as tabelas de estados estão no
[README do OS Service](https://github.com/olavowilke/soat-oficina-api#fase-4--microsserviços-e-saga)
e na [ADR 0006](https://github.com/olavowilke/soat-oficina-api/blob/main/docs/adr/0006-microsservicos-e-saga-orquestrada.md).

## Endpoints

O contexto da aplicação é `/api/billing`. O API Gateway injeta o cabeçalho `x-cliente-id` nas rotas do cliente.
A chamada sem esse cabeçalho vale como chamada da equipe e passa pelo `kubectl port-forward`.

| Método | Caminho | Quem chama | Acesso |
|---|---|---|---|
| `GET` | `/orcamentos/os/{osId}` | Cliente (só o dono) ou equipe | Gateway (CPF) ou port-forward |
| `POST` | `/orcamentos/{id}/aprovar` | Cliente (só o dono) ou equipe. Devolve o link de pagamento | Gateway (CPF) ou port-forward |
| `POST` | `/orcamentos/{id}/recusar` | Cliente (só o dono) ou equipe. Use no port-forward para um orçamento abandonado | Gateway (CPF) ou port-forward |
| `POST` | `/public/webhooks/mercadopago` | Mercado Pago. Existe só com `BILLING_MERCADOPAGO_MODO=real`. Valida o `x-signature` | Gateway, rota pública |
| `GET` | `/public/fake-pagamentos/{pagamentoId}` | Cliente no navegador. Página com os botões aprovar e rejeitar. Existe só no modo `fake` | Gateway, rota pública |
| `POST` | `/public/fake-pagamentos/{pagamentoId}/aprovar` | Botão da página fake | Gateway, rota pública |
| `POST` | `/public/fake-pagamentos/{pagamentoId}/rejeitar` | Botão da página fake | Gateway, rota pública |
| `GET` | `/actuator/health` | Gateway, smoke test do CD e Kubernetes | Gateway, rota pública |
| `GET` | `/actuator/prometheus` | `nri-bundle` (New Relic) | Só dentro do cluster |
| `GET` | `/swagger-ui/index.html`, `/v3/api-docs` | Pessoas | Gateway, rota pública |

O corpo do orçamento (`OrcamentoResponse`) traz `id`, `osId`, `clienteId`, `status`, `valorTotal`, `itens`, `pagamento` e `linkPagamento`.

### Swagger

Pelo API Gateway:

```
$API/api/billing/swagger-ui/index.html
$API/api/billing/v3/api-docs
```

`$API` é o endpoint do API Gateway (SSM `/oficina/homolog/gateway/endpoint`).
Local: `http://localhost:8080/api/billing/swagger-ui.html`, que redireciona para `swagger-ui/index.html`.

## Mensagens

Billing consome `oficina-homolog-billing-commands` e publica em `oficina-homolog-os-saga-replies`.
Todas as mensagens usam o envelope `MensagemSaga`: `messageId`, `sagaId`, `osId`, `type`, `correlationId`, `occurredAt` e `payload`.
Os atributos SQS `type`, `correlationId` e `newrelic` repetem os dados de rastreio.

### Entrada (comandos do OS Service)

| `type` | Campos do `payload` | O que o Billing faz |
|---|---|---|
| `GerarOrcamento` | `clienteId`, `itens[]` (`tipo` `SERVICO` ou `PECA`, `referenciaId`, `descricao`, `quantidade`, `valorUnitario`) | Cria o orçamento `GERADO` |
| `EstornarPagamento` | `motivo` | Estorna o pagamento no Mercado Pago |

### Saída (respostas à fila da OS)

| `type` | Campos do `payload` | Quando |
|---|---|---|
| `OrcamentoGerado` | `orcamentoId`, `valorTotal` | O orçamento foi criado |
| `OrcamentoFalhou` | `motivo` | `GerarOrcamento` inválido (sem itens ou total zero) |
| `OrcamentoAprovado` | `orcamentoId`, `pagamentoId`, `linkPagamento` | O cliente aprovou |
| `OrcamentoRecusado` | `orcamentoId` | O cliente recusou |
| `PagamentoConfirmado` | `orcamentoId`, `pagamentoId` | O pagamento foi aprovado |
| `PagamentoFalhou` | `orcamentoId`, `pagamentoId`, `motivo` (`REJEITADO` ou `EXPIRADO`) | O pagamento foi rejeitado ou venceu |
| `PagamentoEstornado` | `pagamentoId` | O estorno terminou |
| `EstornoFalhou` | `pagamentoId` (`null` se a OS não tem orçamento nem pagamento), `motivo` | O estorno falhou na última entrega |

O dinheiro viaja como texto com duas casas (`"350.00"`). A hora segue o ISO-8601 em UTC.

### Garantias

- **Outbox.** O Billing grava a resposta na tabela `outbox` na mesma transação do dado. Um job envia a cada segundo (`BILLING_OUTBOX_INTERVALO`).
- **Idempotência.** A tabela `mensagens_processadas` descarta um `messageId` repetido.
- **Regra do estorno.** O Billing lê o `ApproximateReceiveCount` do SQS. Se o Mercado Pago recusa o estorno, ele não responde e a fila entrega de novo.
  Na 3ª entrega ele publica `EstornoFalhou`. `BILLING_ESTORNO_MAX_ENTREGAS` precisa ser igual ao `maxReceiveCount` da fila (3).
- **Rastreio.** O consumidor aceita o cabeçalho `newrelic` com `TransportType.Other`. O `newrelic-api` 8.16.0 não tem `TransportType.Queue`.
- **DLQ.** A fila `oficina-homolog-billing-commands-dlq` recebe a mensagem após 3 entregas sem sucesso.

## Banco de dados

PostgreSQL 16 próprio (RDS `oficina-homolog-billing`, `db.t4g.micro`). O Flyway cria o esquema em `V1__schema_billing.sql`.

| Tabela | Conteúdo |
|---|---|
| `orcamentos`, `itens_orcamento` | Orçamento e itens. Status: `GERADO`, `APROVADO`, `RECUSADO`, `CANCELADO`, `PAGO`, `ESTORNADO` |
| `pagamentos` | Pagamento do orçamento. Status: `PENDENTE`, `APROVADO`, `REJEITADO`, `EXPIRADO`, `ESTORNADO` |
| `outbox` | Respostas à espera de envio |
| `mensagens_processadas` | IDs de mensagem já tratados |
| `estornos_avulsos` | Auditoria de estornos fora da Saga (pagamento tardio) |

Nenhum outro serviço acessa este banco.

## Como rodar localmente

Pré-requisitos: Java 21, Docker e o `./mvnw` do repositório.

```bash
docker compose up -d          # Postgres (billing_db) e LocalStack (SQS)
export SPRING_CLOUD_AWS_ENDPOINT=http://localhost:4566
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
./mvnw -s settings.xml spring-boot:run
```

A aplicação sobe em `http://localhost:8080/api/billing`. O listener cria a fila de comandos vazia no LocalStack.
A DLQ não nasce sozinha. O job de métricas avisa no log e segue.

| Variável | Padrão | Para quê |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/billing_db`, `billing`, `billing` | Banco |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil |
| `BILLING_FILAS_COMANDOS` | `oficina-homolog-billing-commands` | Fila de entrada |
| `BILLING_FILAS_COMANDOS_DLQ` | `oficina-homolog-billing-commands-dlq` | DLQ lida pelas métricas |
| `BILLING_FILAS_RESPOSTAS` | `oficina-homolog-os-saga-replies` | Fila das respostas |
| `BILLING_MERCADOPAGO_MODO` | `fake` | `fake` simula o pagamento. `real` usa o sandbox |
| `BILLING_MERCADOPAGO_ACCESS_TOKEN`, `BILLING_MERCADOPAGO_WEBHOOK_SECRET` | vazio | Obrigatórios com `real`. Sem eles a aplicação não sobe |
| `BILLING_URL_PUBLICA` | `http://localhost:8080` | Base do link de pagamento e da URL do webhook |
| `BILLING_PAGAMENTO_PRAZO` | `PT30M` | Prazo do pagamento antes de expirar |
| `BILLING_PAGAMENTO_VERIFICACAO` | `PT30S` | Intervalo do job que expira pagamentos |
| `BILLING_ESTORNO_MAX_ENTREGAS` | `3` | Entregas antes de `EstornoFalhou` |
| `BILLING_FAKE_FALHAR_ESTORNO` | `false` | No modo `fake`, faz o estorno falhar (teste da regra de entregas) |
| `BILLING_OUTBOX_INTERVALO` | `PT1S` | Intervalo do envio do outbox |
| `AWS_REGION` | `us-east-1` | Região |

Para encurtar o prazo na demo, use `BILLING_PAGAMENTO_PRAZO=PT1M`.

## Testes e cobertura

```bash
./mvnw -s settings.xml verify    # precisa do Docker (Testcontainers: Postgres e LocalStack)
```

- A suíte tem testes unitários, testes de integração (`*IT`, com Postgres e LocalStack), o WireMock do Mercado Pago e o ArchUnit.
- O JaCoCo falha o build se a cobertura de linhas do conjunto ficar abaixo de 80%.
- Relatório JaCoCo local: `target/site/jacoco/index.html`, depois de `./mvnw verify`.
- Cobertura no Sonar: [![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=olavowilke_soat-billing-service&metric=coverage)](https://sonarcloud.io/project/overview?id=olavowilke_soat-billing-service)

A captura do relatório JaCoCo entra aqui depois do primeiro `verify` limpo.

## Deploy

O fluxo vive em `.github/workflows/`.

1. **CI** (PR e push em `main`). Jobs: `build-and-test` (`mvn verify`, kustomize e Sonar), `owasp` e `Terraform fmt e validate`.
2. **Branch protegida.** `main` exige PR e os checks do CI.
3. **CD** (push em `main`). Só roda se a variável `DEPLOY_HOMOLOG` for `true`.
   - Job `terraform`: cria fila e DLQ, RDS, ECR, role do pod, listener do NLB (porta 8081) e rotas do gateway.
   - Job `build-and-deploy`: cria o Secret `billing-secrets`, envia a imagem `sha-<git sha>` ao ECR, aplica `k8s/overlays/homolog`, espera o rollout e chama `/api/billing/actuator/health` pelo gateway.

| Nome | Tipo | Para quê |
|---|---|---|
| `AWS_DEPLOY_ROLE_ARN`, `TF_STATE_BUCKET`, `TF_LOCK_TABLE` | Secret | Acesso AWS por OIDC e estado do Terraform |
| `NEW_RELIC_LICENSE_KEY` | Secret | Agente Java do New Relic. Vazio, o agente não liga |
| `DEPLOY_HOMOLOG` | Variable | Precisa ser `true` para o CD rodar |
| `AWS_REGION` | Variable | Opcional. O padrão é `us-east-1` |
| `SONAR_TOKEN` | Secret | Sem ele, o passo Sonar é pulado. Precisa da conta SonarCloud |
| `SONAR_ORGANIZATION` | Variable | Organização do SonarCloud |
| `NVD_API_KEY` | Secret | Chave gratuita da NVD. Sem ela, o job `owasp` é pulado |

O Terraform cria o segredo `oficina/homolog/billing/mercadopago` no Secrets Manager com valores `CHANGE_ME`.
Preencha `access_token` e `webhook_secret` ali. O ConfigMap fixa `BILLING_MERCADOPAGO_MODO=fake`.
Para usar o sandbox, mude o modo para `real` em `k8s/base/configmap.yaml`.

## Operação

- **Métricas.** `/actuator/prometheus` expõe `oficina_fila_dlq_mensagens{fila}`. O `nri-bundle` coleta pelas anotações do pod.
  O alerta "Saga - mensagens em DLQ" abre quando o valor passa de 0 por 5 minutos.
- **Logs.** Logs JSON com `correlationId` e `sagaId`. Busque pelo `sagaId` no New Relic para seguir uma OS.
- **Mensagem na DLQ.** Leia a mensagem na DLQ, corrija a causa e reenvie à fila de comandos. O reprocessamento é manual.

### Limites conhecidos

- **Orçamento `GERADO` não expira.** Se o cliente não responde, a Saga fica em `AGUARDANDO_APROVACAO`.
  A equipe recusa por `kubectl port-forward -n billing svc/billing-service 8080:8080`, com `POST /api/billing/orcamentos/{id}/recusar` sem o cabeçalho `x-cliente-id`.
- **O link do Mercado Pago não expira.** O Billing expira o pagamento após `BILLING_PAGAMENTO_PRAZO` e responde `PagamentoFalhou` com `EXPIRADO`.
  Se o cliente paga depois, o Billing estorna sozinho e grava em `estornos_avulsos`. Não há resposta à Saga nesse caso.
- **Estorno tardio que falha.** O Billing audita em `estornos_avulsos` e responde erro ao webhook. O Mercado Pago repete o aviso.
  Se a falha continuar, um operador estorna no painel do Mercado Pago.
- **Estorno da Saga que falha.** Depois de 3 entregas, o Billing envia `EstornoFalhou`. A Saga vai para `FALHOU`.
  Um ADMIN usa `POST /ordens-servico/{id}/saga/estorno` ou `/saga/cancelar-apos-estorno-manual` no OS Service.
- **Só sandbox.** O serviço não tem configuração de produção do Mercado Pago.
