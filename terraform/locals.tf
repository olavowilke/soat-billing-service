data "aws_caller_identity" "current" {}

locals {
  prefixo  = "${var.project}-${var.environment}"
  ssm_base = "/${var.project}/${var.environment}"

  nome_servico = "billing-service"
  nome_fila    = "${local.prefixo}-billing-commands"
  nome_banco   = "billing_db"
  usuario_db   = "billing"

  # A fila de respostas pertence a oficina-api. Nome fixo (contrato da Fase 4):
  # este servico so envia para ela e nao depende da ordem de apply.
  fila_respostas_arn = "arn:aws:sqs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:${local.prefixo}-os-saga-replies"
}
