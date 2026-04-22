variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "vpc_link_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for the API Gateway VPC Link ENIs"
}

variable "vpc_link_security_group_id" {
  type        = string
  description = "Security group ID for the API Gateway VPC Link ENIs (needs egress to ALB:443)"
}

variable "alb_listener_arn" {
  type        = string
  description = "ARN of the internal ALB HTTPS listener (HTTP API integrates via VPC Link v2 directly with the ALB listener)"
}

variable "cognito_user_pool_id" {
  type        = string
  description = "Cognito User Pool ID for the JWT authorizer issuer URL"
}

variable "cognito_user_pool_arn" {
  type        = string
  description = "ARN of the Cognito User Pool (kept for parity with REST API usage)"
}

variable "cognito_client_ids" {
  type        = list(string)
  description = "Cognito client IDs accepted as JWT audience by the HTTP API authorizer"
}

variable "access_function_invoke_arn" {
  type        = string
  description = "Invoke ARN of the access Lambda function"
}

variable "access_function_arn" {
  type        = string
  description = "ARN of the access Lambda function"
}

variable "access_function_name" {
  type        = string
  description = "Name of the access Lambda function"
}

variable "domain_name" {
  type        = string
  default     = ""
  description = "Custom domain name for API Gateway (empty = no custom domain)"
}

variable "acm_cert_arn" {
  type        = string
  default     = ""
  description = "ACM certificate ARN for custom domain"
}
