data "aws_ssm_parameter" "api_id" {
  name = "${local.ssm_base}/gateway/api-id"
}

data "aws_ssm_parameter" "authorizer_id" {
  name = "${local.ssm_base}/gateway/authorizer-id"
}

data "aws_ssm_parameter" "vpc_link_id" {
  name = "${local.ssm_base}/network/vpc-link-id"
}

# Integracao privada: o trafego nunca sai da VPC para chegar ao cluster.
# O stage $default (auto_deploy = true) pertence a soat-lambda-auth e
# publica estas rotas sozinho.
resource "aws_apigatewayv2_integration" "billing_service" {
  api_id             = data.aws_ssm_parameter.api_id.value
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = aws_lb_listener.billing_service.arn
  connection_type    = "VPC_LINK"
  connection_id      = data.aws_ssm_parameter.vpc_link_id.value

  # Correlaciona a requisicao do gateway com o log da aplicacao (MDC correlationId).
  request_parameters = {
    "overwrite:header.x-correlation-id" = "$context.requestId"
    "overwrite:header.x-cliente-id"     = "$context.authorizer.clienteId"
  }
}

# Rota do cliente: exige o JWT validado pelo authorizer CPF. Mais especifica
# que `ANY /api/{proxy+}` (oficina-api), entao ganha dela.
resource "aws_apigatewayv2_route" "protegida" {
  api_id             = data.aws_ssm_parameter.api_id.value
  route_key          = "ANY /api/billing/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.billing_service.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = data.aws_ssm_parameter.authorizer_id.value
}

# Rotas publicas (sem authorizer): webhook, health e documentacao.
locals {
  rotas_publicas = [
    "ANY /api/billing/public/{proxy+}",
    "GET /api/billing/actuator/health",
    "GET /api/billing/swagger-ui.html",
    "GET /api/billing/swagger-ui/{proxy+}",
    "GET /api/billing/v3/api-docs",
    "GET /api/billing/v3/api-docs/{proxy+}",
  ]
}

resource "aws_apigatewayv2_route" "publicas" {
  for_each = toset(local.rotas_publicas)

  api_id    = data.aws_ssm_parameter.api_id.value
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.billing_service.id}"
}
