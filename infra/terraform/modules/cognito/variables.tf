variable "env_suffix" {
  type        = string
  description = "Environment suffix (dev, prod)"
}

variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS region for Cognito"
}

variable "ses_sender_email" {
  type        = string
  description = "SES verified sender email for Cognito emails"
}

variable "ses_sender_identity_arn" {
  type        = string
  description = "ARN of the verified SES sender identity"
}

variable "use_ses_email" {
  type        = bool
  default     = false
  description = "Use SES (DEVELOPER) for Cognito emails. Only enable if the SES identity is fully verified in a supported region."
}

variable "callback_urls" {
  type        = list(string)
  default     = ["http://localhost:3000/callback"]
  description = "OAuth2 callback URLs"
}

variable "logout_urls" {
  type        = list(string)
  default     = ["http://localhost:3000"]
  description = "OAuth2 logout URLs"
}
