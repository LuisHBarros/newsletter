variable "vpc_id" {
  type        = string
  description = "VPC ID"
}

variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for NLB"
}

variable "alb_arn" {
  type        = string
  description = "ALB ARN to forward traffic to"
}

variable "certificate_arn" {
  type        = string
  default     = ""
  description = "ACM certificate ARN for TLS on NLB listener"
}
