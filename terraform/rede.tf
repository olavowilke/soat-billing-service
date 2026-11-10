data "aws_ssm_parameter" "vpc_id" {
  name = "${local.ssm_base}/network/vpc-id"
}

data "aws_ssm_parameter" "nlb_arn" {
  name = "${local.ssm_base}/network/nlb-arn"
}

# Lista separada por virgula, publicada por soat-infra-k8s.
data "aws_ssm_parameter" "node_asg_names" {
  name = "${local.ssm_base}/network/node-asg-names"
}

locals {
  # O valor do SSM e sensivel por padrao; nonsensitive() deixa usar em for_each.
  # Nao e segredo: sao nomes de ASG.
  asg_names = toset(compact([for n in split(",", nonsensitive(data.aws_ssm_parameter.node_asg_names.value)) : trimspace(n)]))
}

resource "aws_lb_target_group" "billing_service" {
  name        = "${local.prefixo}-billing"
  port        = var.node_port
  protocol    = "TCP"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value
  target_type = "instance"

  health_check {
    protocol            = "HTTP"
    path                = "/api/billing/actuator/health"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    interval            = 15
  }
}

resource "aws_lb_listener" "billing_service" {
  load_balancer_arn = data.aws_ssm_parameter.nlb_arn.value
  port              = var.listener_port
  protocol          = "TCP"

  # Sem ASG, o for_each abaixo seria vazio e o NLB nao teria alvos.
  lifecycle {
    precondition {
      condition     = length(local.asg_names) > 0
      error_message = "SSM network/node-asg-names esta vazio."
    }
  }

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.billing_service.arn
  }
}

resource "aws_autoscaling_attachment" "nodes" {
  for_each               = local.asg_names
  autoscaling_group_name = each.value
  lb_target_group_arn    = aws_lb_target_group.billing_service.arn
}
