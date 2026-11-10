#!/bin/sh
# Entrypoint do container billing-service (Fase 4).
#
# O agente Java do New Relic é OPCIONAL: só é anexado via -javaagent quando
# NEW_RELIC_LICENSE_KEY estiver definida no ambiente. Sem ela (docker-compose
# local, CI, um fork sem credenciais do New Relic), a aplicação sobe normal,
# sem o agente — em vez de confiar no comportamento interno do agente para
# uma license key vazia (que não dá para validar aqui, já que não é possível
# rodar `docker build`/`docker run` neste ambiente), a decisão é explícita
# neste script, verificável por leitura.
#
# NEW_RELIC_APP_NAME já chega via ambiente (ver
# infra-k8s/k8s/base/configmap.yaml) e é lida diretamente pelo agente — não
# precisa de tratamento aqui. NEW_RELIC_LABELS e
# NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED NÃO são definidas por
# nenhum manifesto deste projeto — o agente roda sem elas (default). O log
# chega ao New Relic por outro caminho: o DaemonSet Fluent Bit do bundle
# Helm (`newrelic-logging.enabled`, infra-k8s/terraform/newrelic.tf) lê o
# stdout JSON do container direto do nó, sem depender de nada configurado
# neste agente Java.
set -e

JAVA_AGENT_OPTS=""
if [ -n "$NEW_RELIC_LICENSE_KEY" ]; then
    JAVA_AGENT_OPTS="-javaagent:/app/newrelic.jar"
fi

exec java $JAVA_AGENT_OPTS -jar /app/app.jar
