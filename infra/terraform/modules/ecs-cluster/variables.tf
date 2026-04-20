variable "env_suffix" {
  type        = string
  description = "Environment suffix"
}

variable "name" {
  type        = string
  default     = "assine"
  description = "Cluster name prefix"
}

variable "services" {
  type        = list(string)
  description = "List of service names for log group creation"
}
