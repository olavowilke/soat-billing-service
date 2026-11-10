data "aws_ssm_parameter" "private_subnet_ids" {
  name = "${local.ssm_base}/network/private-subnet-ids"
}

# SG "cracha": os nos do EKS o vestem. O SG do banco so precisa do ID dele.
data "aws_ssm_parameter" "db_client_sg_id" {
  name = "${local.ssm_base}/network/db-client-sg-id"
}

locals {
  # O valor do SSM e sensivel por padrao. Nao e segredo: sao IDs de subnet.
  private_subnet_ids = compact([for s in split(",", nonsensitive(data.aws_ssm_parameter.private_subnet_ids.value)) : trimspace(s)])
}

resource "aws_db_subnet_group" "billing" {
  name       = "${local.prefixo}-billing-db"
  subnet_ids = local.private_subnet_ids

  lifecycle {
    precondition {
      condition     = length(local.private_subnet_ids) > 0
      error_message = "SSM network/private-subnet-ids esta vazio."
    }
  }

  tags = {
    Name = "${local.prefixo}-billing-db"
  }
}

resource "aws_security_group" "db" {
  name        = "${local.prefixo}-billing-db"
  description = "Acesso ao RDS do billing-service a partir do SG compartilhado de clientes do banco"
  vpc_id      = data.aws_ssm_parameter.vpc_id.value

  tags = {
    Name = "${local.prefixo}-billing-db"
  }
}

resource "aws_security_group_rule" "db_ingress_postgres" {
  type                     = "ingress"
  security_group_id        = aws_security_group.db.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_ssm_parameter.db_client_sg_id.value
  description              = "PostgreSQL a partir do SG cracha (EKS)"
}

resource "aws_security_group_rule" "db_egress_all" {
  type              = "egress"
  security_group_id = aws_security_group.db.id
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

# A senha master fica no Secrets Manager, gerada pelo RDS. Nunca passa por
# arquivo versionado nem por output do Terraform.
resource "aws_db_instance" "billing" {
  identifier     = "${local.prefixo}-billing"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name                     = local.nome_banco
  username                    = local.usuario_db
  manage_master_user_password = true
  port                        = 5432

  db_subnet_group_name   = aws_db_subnet_group.billing.name
  vpc_security_group_ids = [aws_security_group.db.id]

  multi_az                = false
  publicly_accessible     = false
  backup_retention_period = var.db_backup_retention_days

  auto_minor_version_upgrade = true

  # Homolog: o usuario destroi o ambiente para reduzir custo.
  skip_final_snapshot = true
  deletion_protection = false
  apply_immediately   = true

  tags = {
    Name = "${local.prefixo}-billing"
  }
}
