variable "project" {
  description = "Prefixo de nomes dos recursos"
  type        = string
  default     = "oficina"
}

variable "environment" {
  description = "Ambiente logico"
  type        = string

  validation {
    condition     = contains(["homolog"], var.environment)
    error_message = "environment deve ser homolog."
  }
}

variable "aws_region" {
  description = "Regiao AWS"
  type        = string
  default     = "us-east-1"
}

variable "namespace" {
  description = "Namespace Kubernetes do billing-service"
  type        = string
  default     = "billing"
}

variable "service_account" {
  description = "ServiceAccount Kubernetes usada pelo pod do billing-service"
  type        = string
  default     = "billing-service"
}

variable "node_port" {
  description = "NodePort do Service do billing-service"
  type        = number
  default     = 30081
}

variable "listener_port" {
  description = "Porta do listener do NLB para o billing-service"
  type        = number
  default     = 8081
}

variable "db_instance_class" {
  description = "Classe da instancia RDS do billing-service"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Armazenamento do RDS (GB)"
  type        = number
  default     = 20
}

variable "db_backup_retention_days" {
  description = "Retencao dos backups automaticos (homolog: 1 dia para reduzir custo)"
  type        = number
  default     = 1
}
