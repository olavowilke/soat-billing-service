output "ecr_repository_url" {
  description = "URL do repositorio ECR do billing-service"
  value       = aws_ecr_repository.billing_service.repository_url
}

output "db_endpoint" {
  description = "Endpoint do RDS do billing-service"
  value       = aws_db_instance.billing.address
}

output "db_secret_arn" {
  description = "ARN do segredo da senha master do RDS (gerenciado pelo RDS)"
  value       = aws_db_instance.billing.master_user_secret[0].secret_arn
}

output "fila_comandos_url" {
  description = "URL da fila de comandos"
  value       = aws_sqs_queue.comandos.url
}

output "fila_comandos_arn" {
  description = "ARN da fila de comandos"
  value       = aws_sqs_queue.comandos.arn
}

output "fila_comandos_dlq_arn" {
  description = "ARN da DLQ da fila de comandos"
  value       = aws_sqs_queue.comandos_dlq.arn
}

output "mercadopago_secret_arn" {
  description = "ARN do segredo do Mercado Pago (valores gravados pelo usuario)"
  value       = aws_secretsmanager_secret.mercadopago.arn
}

output "role_arn" {
  description = "ARN da role assumida pelo pod do billing-service"
  value       = aws_iam_role.billing_service.arn
}
