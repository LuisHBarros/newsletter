variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "name" {
  type        = string
  default     = "assine"
  description = "Cluster name prefix"
}

variable "services" {
  type        = list(string)
  description = "List of service names for log group creation"
}

variable "enable_container_insights" {
  type        = bool
  default     = false
  description = "Habilita ECS Container Insights (fora do free tier do CloudWatch). Padrao desabilitado para manter Free Tier + budget."
}

variable "log_retention_days" {
  type        = number
  default     = 7
  description = "Retencao dos log groups /ecs/<env>/<service>. 7 dias eh suficiente para debug recente; reduz ingest CloudWatch."
}
