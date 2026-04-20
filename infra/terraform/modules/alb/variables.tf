variable "vpc_id" {
  type        = string
  description = "VPC ID"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Subnet IDs for ALB (private for internal)"
}

variable "sg_alb_id" {
  type        = string
  description = "Security group ID for ALB"
}

variable "certificate_arn" {
  type        = string
  description = "ACM certificate ARN for HTTPS listener"
}

variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "name" {
  type        = string
  default     = "assine"
  description = "ALB name prefix"
}

variable "access_logs_bucket_name" {
  type        = string
  default     = ""
  description = "S3 bucket name for ALB access logs"
}
