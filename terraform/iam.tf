data "aws_ssm_parameter" "cluster_name" {
  name = "${local.ssm_base}/cluster/name"
}

# Pod Identity: o servico pods.eks.amazonaws.com assume a role em nome do pod.
data "aws_iam_policy_document" "confianca" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "billing_service" {
  name               = "${local.prefixo}-${local.nome_servico}"
  assume_role_policy = data.aws_iam_policy_document.confianca.json
}

# Minimo privilegio: so SQS. O pod nao chama o Secrets Manager. A pipeline de
# CD le o segredo do banco e o do Mercado Pago com a role de deploy e grava
# ambos no Secret do Kubernetes que o pod consome por variavel de ambiente.
data "aws_iam_policy_document" "permissoes" {
  statement {
    sid = "ConsumirComandos"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.comandos.arn]
  }

  statement {
    sid = "LerTamanhoDaDlq"
    actions = [
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.comandos_dlq.arn]
  }

  statement {
    sid = "EnviarRespostas"
    actions = [
      "sqs:SendMessage",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
    ]
    resources = [local.fila_respostas_arn]
  }
}

resource "aws_iam_role_policy" "permissoes" {
  name   = "billing-service"
  role   = aws_iam_role.billing_service.id
  policy = data.aws_iam_policy_document.permissoes.json
}

resource "aws_eks_pod_identity_association" "billing_service" {
  cluster_name    = data.aws_ssm_parameter.cluster_name.value
  namespace       = var.namespace
  service_account = var.service_account
  role_arn        = aws_iam_role.billing_service.arn
}
