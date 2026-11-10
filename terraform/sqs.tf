resource "aws_sqs_queue" "comandos_dlq" {
  name                      = "${local.nome_fila}-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "comandos" {
  name                       = local.nome_fila
  visibility_timeout_seconds = 30
  sqs_managed_sse_enabled    = true

  # maxReceiveCount deve ser igual a billing.estorno.max-entregas (padrao 3,
  # env BILLING_ESTORNO_MAX_ENTREGAS): o estorno desiste na ultima entrega,
  # antes de a fila mover a mensagem para a DLQ.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.comandos_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "comandos_dlq" {
  queue_url = aws_sqs_queue.comandos_dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.comandos.arn]
  })
}
