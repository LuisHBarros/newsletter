variable "repos" {
  type        = list(string)
  description = "List of ECR repository names (without prefix)"
}

variable "prefix" {
  type        = string
  default     = "assine"
  description = "Prefix for ECR repository names"
}
