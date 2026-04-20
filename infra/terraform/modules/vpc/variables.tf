variable "cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "VPC CIDR block"
}

variable "azs" {
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
  description = "Availability zones"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
  description = "Public subnet CIDR blocks"
}

variable "private_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
  description = "Private subnet CIDR blocks"
}

variable "enable_nat" {
  type        = bool
  default     = false
  description = "Create NAT Gateway (prod only)"
}

variable "enable_vpc_flow_logs" {
  type        = bool
  default     = false
  description = "Enable VPC Flow Logs"
}

variable "vpc_flow_logs_retention_days" {
  type        = number
  default     = 7
  description = "VPC Flow Logs retention in days"
}

variable "enable_vpc_endpoints" {
  type        = bool
  default     = false
  description = "Enable VPC Endpoints"
}

variable "alb_logs_bucket_name" {
  type        = string
  default     = ""
  description = "ALB Access Logs S3 bucket name (empty to skip creation)"
}

variable "alb_logs_retention_days" {
  type        = number
  default     = 7
  description = "ALB Access Logs retention in days"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region"
}

variable "private_subnet_ids" {
  type        = list(string)
  default     = []
  description = "Private subnet IDs for VPC endpoints"
}
