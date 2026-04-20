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
  type        = list(string)
  description = "List of ALB target group ARNs to monitor"
}
