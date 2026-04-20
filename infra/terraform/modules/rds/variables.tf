variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for RDS subnet group"
}

variable "sg_rds_id" {
  type        = string
  description = "Security group ID for RDS"
}

variable "databases" {
  type = map(object({
    name = string
    user = string
  }))
  default = {
    subscriptions = { name = "newsletter_subscriptions", user = "subscriptions_app" }
    billing       = { name = "newsletter_billing", user = "billing_app" }
    content       = { name = "newsletter_content", user = "content_app" }
  }
  description = "Map of databases to create with their users"
}

variable "backup_retention_period" {
  type        = number
  default     = 7
  description = "RDS backup retention in days"
}

variable "deletion_protection" {
  type        = bool
  default     = true
  description = "Enable deletion protection"
}

variable "skip_final_snapshot" {
  type        = bool
  default     = false
  description = "Skip final snapshot on deletion"
}

variable "env_suffix" {
  type        = string
  default     = "prod"
  description = "Environment suffix for resource naming"
}

variable "lambda_subnet_ids" {
  type        = list(string)
  default     = []
  description = "Private subnet IDs for the bootstrap Lambda"
}

variable "lambda_security_group_id" {
  type        = string
  default     = ""
  description = "Security group ID for the bootstrap Lambda"
}

variable "multi_az" {
  type        = bool
  default     = false
  description = "Enable Multi-AZ deployment"
}

variable "enable_secret_rotation" {
  type        = bool
  default     = false
  description = "Enable automatic secret rotation"
}

variable "secret_rotation_days" {
  type        = number
  default     = 30
  description = "Secret rotation interval in days"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_account_id" {
  type    = string
  default = ""
}
