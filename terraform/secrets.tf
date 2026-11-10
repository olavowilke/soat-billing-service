# Segredo do Mercado Pago. O Terraform cria so o envelope com valores de
# exemplo; o usuario grava os valores reais depois (console ou CLI).
# O pod nao le este segredo: a pipeline de CD o le com a role de deploy e
# monta o Secret do Kubernetes (BILLING_MERCADOPAGO_*).
resource "aws_secretsmanager_secret" "mercadopago" {
  name                    = "${var.project}/${var.environment}/billing/mercadopago"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mercadopago" {
  secret_id = aws_secretsmanager_secret.mercadopago.id
  secret_string = jsonencode({
    access_token   = "CHANGE_ME"
    webhook_secret = "CHANGE_ME"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
