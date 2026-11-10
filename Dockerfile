# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

COPY settings.xml .

RUN chmod +x mvnw
RUN ./mvnw -s settings.xml dependency:go-offline -B

COPY src ./src
RUN ./mvnw -s settings.xml package -DskipTests -B

# Stage 2: Agente Java do New Relic (Fase 4)
# Estágio isolado só para baixar e extrair o agente — evita instalar
# curl/unzip na imagem final que roda em produção. Versão fixada (não
# "latest") para builds reprodutíveis; atualizar aqui deliberadamente quando
# preciso.
FROM alpine:3.20 AS newrelic-agent
ARG NEWRELIC_AGENT_VERSION=8.17.0
WORKDIR /tmp
RUN apk add --no-cache curl unzip \
    && curl -fsSL -o newrelic-java.zip \
        "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEWRELIC_AGENT_VERSION}/newrelic-java.zip" \
    && unzip -q newrelic-java.zip \
    && mv newrelic/newrelic.jar /tmp/newrelic.jar

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S oficina && adduser -S oficina -G oficina

COPY --from=builder /app/target/*.jar app.jar
COPY --from=newrelic-agent /tmp/newrelic.jar newrelic.jar
COPY entrypoint.sh entrypoint.sh

# chmod/chown ainda como root (usuário não-root só é assumido depois, na
# execução) — mantém o mesmo usuário não-root já existente antes desta
# tarefa, apenas movido para depois destes ajustes de permissão.
RUN chmod +x entrypoint.sh && chown -R oficina:oficina /app

USER oficina

EXPOSE 8080

# Healthcheck via actuator (wget vem do busybox no alpine). O context-path é /api.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/api/billing/actuator/health | grep -q '"status":"UP"' || exit 1

# NEW_RELIC_APP_NAME e NEW_RELIC_LICENSE_KEY vêm do ambiente (ver
# infra-k8s/k8s/base/configmap.yaml e deployment.yaml) e são lidos
# diretamente pelo agente New Relic — nenhum ENV fixo aqui. NEW_RELIC_LABELS
# e NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED NÃO são definidas por
# nenhum manifesto deste projeto — o agente roda sem elas (labels vazias;
# forwarding de log do próprio agente desligado, que é o default). O log
# chega ao New Relic por outro caminho, de qualquer forma: o DaemonSet
# Fluent Bit do bundle Helm (`newrelic-logging.enabled`, ver
# infra-k8s/terraform/newrelic.tf) lê o stdout JSON do container direto do
# nó — não depende de nenhuma configuração deste agente Java. O agente é
# opcional: o entrypoint.sh só o anexa via -javaagent quando
# NEW_RELIC_LICENSE_KEY não estiver vazia, então a execução local
# (docker-compose, sem essa variável) sobe normalmente, sem o agente.
ENTRYPOINT ["/app/entrypoint.sh"]
