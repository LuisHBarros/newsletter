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
  description = "ECS cluster name for AWS/ECS metrics"
}

variable "alb_arn_suffix" {
  type        = string
  description = "ALB ARN suffix (app/<name>/<id>) for CloudWatch LoadBalancer dimension"
}

variable "target_group_arn_suffixes" {
  type        = map(string)
  description = "Map of service name -> target group ARN suffix (targetgroup/<name>/<id>). Keys devem ser estaticos (conhecidos no plan) para viabilizar for_each mesmo quando os ARNs ainda nao existem."
}
