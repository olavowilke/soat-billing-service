# Publicados para o manifesto do Deployment, a pipeline e outras stacks
# consumirem via `data`, sem acoplar remote states (ADR-0005).
resource "aws_ssm_parameter" "fila_comandos_url" {
  name  = "${local.ssm_base}/queues/billing-commands/url"
  type  = "String"
  value = aws_sqs_queue.comandos.url
}

resource "aws_ssm_parameter" "fila_comandos_arn" {
  name  = "${local.ssm_base}/queues/billing-commands/arn"
  type  = "String"
  value = aws_sqs_queue.comandos.arn
}

resource "aws_ssm_parameter" "ecr_repository_url" {
  name  = "${local.ssm_base}/registry/billing-service/repository-url"
  type  = "String"
  value = aws_ecr_repository.billing_service.repository_url
}

resource "aws_ssm_parameter" "db_endpoint" {
  name  = "${local.ssm_base}/billing/db/endpoint"
  type  = "String"
  value = aws_db_instance.billing.address
}

resource "aws_ssm_parameter" "db_secret_arn" {
  name  = "${local.ssm_base}/billing/db/secret-arn"
  type  = "String"
  value = aws_db_instance.billing.master_user_secret[0].secret_arn
}
