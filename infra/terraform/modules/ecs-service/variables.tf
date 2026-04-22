variable "name" {
  type        = string
  description = "Service name (billing, content, subscriptions)"
}

variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "cluster_arn" {
  type        = string
  description = "ECS cluster ARN"
}

variable "image" {
  type        = string
  description = "Container image URI (ECR URL + tag)"
}

variable "cpu" {
  type        = number
  default     = 512
  description = "CPU units"
}

variable "memory" {
  type        = number
  default     = 1024
  description = "Memory in MiB"
}

variable "container_port" {
  type        = number
  default     = 8080
  description = "Container port"
}

variable "desired_count" {
  type        = number
  default     = 1
  description = "Desired task count"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID for target group"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Subnet IDs for the ECS service tasks. Pode ser publicas (com assign_public_ip=true) para evitar NAT Gateway no Free Tier/budget."
}

variable "assign_public_ip" {
  type        = bool
  default     = false
  description = "Atribui IP publico as ENIs das tasks. Necessario quando rodam em subnets publicas sem NAT para egress (ECR/Secrets/Logs)."
}

variable "use_fargate_spot" {
  type        = bool
  default     = false
  description = "Usa FARGATE_SPOT via capacity_provider_strategy (ate -70% no custo). Requer FARGATE_SPOT no cluster."
}

variable "security_group_ids" {
  type        = list(string)
  description = "Security group IDs for tasks"
}

variable "task_execution_role_arn" {
  type        = string
  description = "Task execution role ARN"
}

variable "task_role_arn" {
  type        = string
  description = "Task role ARN"
}

variable "listener_arn" {
  type        = string
  description = "ALB listener ARN"
}

variable "path_patterns" {
  type        = list(string)
  description = "ALB path patterns for routing"
}

variable "priority" {
  type        = number
  description = "Listener rule priority"
}

variable "environment" {
  type = list(object({
    name  = string
    value = string
  }))
  default     = []
  description = "Environment variables"
}

variable "secrets" {
  type = list(object({
    name      = string
    valueFrom = string
  }))
  default     = []
  description = "Secrets from Secrets Manager"
}

variable "log_group_name" {
  type        = string
  description = "CloudWatch log group name"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region for log group"
}

variable "enable_otel_sidecar" {
  type        = bool
  default     = false
  description = "Add aws-otel-collector sidecar"
}
