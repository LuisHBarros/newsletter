variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "alert_email" {
  type        = string
  description = "Email address for CloudWatch alarm notifications"
}

variable "service_names" {
  type        = list(string)
  description = "List of service names (e.g., [billing, content, subscriptions])"
}

variable "dlq_arns" {
  type        = list(string)
  description = "List of DLQ ARNs to monitor"
}

variable "cluster_name" {
  type        = string
  description = "ECS cluster name for Container Insights metrics"
}

variable "target_group_arns" {
  type        = map(string)
  description = "Map of service name -> ALB target group ARN. Keys devem ser estaticos (conhecidos no plan) para viabilizar for_each mesmo quando os ARNs ainda nao existem."
}
