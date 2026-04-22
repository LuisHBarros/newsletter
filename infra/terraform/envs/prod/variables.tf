variable "env_suffix" {
  type        = string
  description = "Environment suffix for resource names"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region for all resources"
}

variable "acm_cert_arn" {
  type        = string
  description = "ACM certificate ARN for HTTPS listener"
}

variable "github_repo" {
  type        = string
  description = "GitHub repo in format owner/repo for OIDC"
}

variable "ses_sender_email" {
  type        = string
  description = "SES verified sender email for Cognito"
}

variable "ses_sender_identity_arn" {
  type        = string
  description = "ARN of the verified SES sender identity for Cognito"
}

variable "api_domain_name" {
  type        = string
  default     = ""
  description = "Custom domain name for API Gateway (empty = no custom domain)"
}

variable "alert_email" {
  type        = string
  description = "Email address for CloudWatch alarm notifications"
}
