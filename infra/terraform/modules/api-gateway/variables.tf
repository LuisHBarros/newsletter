variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "nlb_dns" {
  type        = string
  description = "DNS name of the internal NLB"
}

variable "nlb_arn" {
  type        = string
  description = "ARN of the internal NLB"
}

variable "cognito_user_pool_arn" {
  type        = string
  description = "ARN of the Cognito User Pool"
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

variable "api_gateway_log_group_arn" {
  type        = string
  description = "CloudWatch log group ARN for API Gateway access logs"
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

variable "openapi_body" {
  type        = string
  description = "Rendered OpenAPI spec body with all substitutions applied"
  default     = ""
}

variable "openapi_spec_path" {
  type        = string
  description = "Path to the OpenAPI spec YAML file for templatefile()"
  default     = ""
}
