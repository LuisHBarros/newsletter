variable "vpc_id" {
  type        = string
  description = "VPC ID to attach security groups to"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block for restricting access"
}
