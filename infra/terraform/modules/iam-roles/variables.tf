variable "env_suffix" {
  type        = string
  description = "Environment suffix (dev, prod)"
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

variable "secrets_arns" {
  type        = list(string)
  default     = []
  description = "List of Secrets Manager ARNs the task execution role can read"
}

variable "kms_key_arn" {
  type        = string
  default     = ""
  description = "KMS key ARN for decryption (optional)"
}

variable "ecr_repo_arns" {
  type        = list(string)
  default     = []
  description = "ECR repository ARNs for GitHub Actions OIDC role"
}

variable "ecs_cluster_name" {
  type        = string
  default     = ""
  description = "ECS cluster name for GitHub Actions OIDC role"
}

variable "lambda_function_names" {
  type        = list(string)
  default     = []
  description = "Lambda function names for GitHub Actions OIDC role"
}

variable "github_repo" {
  type        = string
  description = "GitHub repo in format owner/repo"
}

variable "sqs_arns" {
  type = object({
    events        = string
    subscriptions = string
    content_jobs  = string
  })
  description = "SQS queue ARNs for task role policies"
}

variable "content_bucket_arn" {
  type        = string
  default     = ""
  description = "S3 bucket ARN for content service"
}

variable "dynamodb_table_arns" {
  type = object({
    notifications = string
    access        = string
  })
  description = "DynamoDB table ARNs for Lambda policies"
}

variable "ses_sender_email" {
  type        = string
  default     = ""
  description = "SES sender email address for condition key"
}
