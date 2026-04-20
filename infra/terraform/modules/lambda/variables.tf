variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "aws_account_id" {
  type        = string
  description = "AWS Account ID"
}

variable "ecr_repository_urls" {
  type        = map(string)
  description = "Map of service name to ECR repository URL"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for Lambda VPC config"
  default     = []
}

variable "security_group_ids" {
  type        = list(string)
  description = "Security group IDs for Lambda VPC config"
  default     = []
}

variable "sqs_event_queue_arn" {
  type        = string
  default     = ""
  description = "SQS queue ARN for notifications Lambda event source"
}

variable "ses_sender_email" {
  type        = string
  default     = ""
  description = "SES sender email for notifications Lambda"
}

variable "access_role_arn" {
  type        = string
  description = "IAM role ARN for access Lambda"
}

variable "notifications_role_arn" {
  type        = string
  description = "IAM role ARN for notifications Lambda"
}
