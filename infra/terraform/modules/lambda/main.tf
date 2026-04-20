terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_lambda_function" "access" {
  function_name = "assine-access-${var.env_suffix}"
  role          = var.access_role_arn
  package_type  = "Image"
  image_uri     = "${var.ecr_repository_urls["access"]}:latest"

  timeout     = 10
  memory_size = 256

  tracing_config {
    mode = "Active"
  }

  environment {
    variables = {
      LOG_LEVEL = "info"
    }
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = var.security_group_ids
  }

  tags = {
    Name        = "assine-access-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_lambda_function" "notifications" {
  function_name = "assine-notifications-${var.env_suffix}"
  role          = var.notifications_role_arn
  package_type  = "Image"
  image_uri     = "${var.ecr_repository_urls["notifications"]}:latest"

  timeout     = 30
  memory_size = 256

  tracing_config {
    mode = "Active"
  }

  environment {
    variables = {
      LOG_LEVEL    = "info"
      SENDER_EMAIL = var.ses_sender_email
    }
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = var.security_group_ids
  }

  tags = {
    Name        = "assine-notifications-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_lambda_event_source_mapping" "notifications_sqs" {
  count = var.sqs_event_queue_arn != "" ? 1 : 0

  event_source_arn = var.sqs_event_queue_arn
  function_name    = aws_lambda_function.notifications.arn
  batch_size       = 1

  function_response_types = ["ReportBatchItemFailures"]
}
