terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_api_gateway_rest_api" "main" {
  name = "assine-api-${var.env_suffix}"

  body = templatefile(
    var.openapi_spec_path,
    {
      api_gateway_id             = "PLACEHOLDER"
      aws_region                 = var.aws_region
      env_suffix                 = var.env_suffix
      nlb_dns                    = var.nlb_dns
      vpc_link_id                = aws_api_gateway_vpc_link.main.id
      access_function_invoke_arn = var.access_function_invoke_arn
      api_gateway_role_arn       = aws_iam_role.api_gateway.arn
      cognito_user_pool_arn      = var.cognito_user_pool_arn
    }
  )

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  parameters = {
    "endpoint_configuration.types" = "REGIONAL"
  }

  tags = {
    Name        = "assine-api-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_api_gateway_vpc_link" "main" {
  name        = "assine-vpc-link-${var.env_suffix}"
  description = "VPC Link to NLB for ECS services"
  target_arns = [var.nlb_arn]
}

resource "aws_api_gateway_deployment" "main" {
  rest_api_id = aws_api_gateway_rest_api.main.id

  triggers = {
    redeployment = sha1(aws_api_gateway_rest_api.main.body)
  }

  depends_on = [
    aws_api_gateway_rest_api.main,
    aws_api_gateway_vpc_link.main,
  ]

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "main" {
  deployment_id = aws_api_gateway_deployment.main.id
  rest_api_id   = aws_api_gateway_rest_api.main.id
  stage_name    = var.env_suffix

  xray_tracing_enabled = true

  access_log_settings {
    destination_arn = var.api_gateway_log_group_arn
    format = jsonencode({
      requestId          = "$context.requestId"
      ip                 = "$context.identity.sourceIp"
      caller             = "$context.identity.caller"
      user               = "$context.identity.user"
      requestTime        = "$context.requestTime"
      httpMethod         = "$context.httpMethod"
      resourcePath       = "$context.resourcePath"
      status             = "$context.status"
      protocol           = "$context.protocol"
      responseLength     = "$context.responseLength"
      integrationError   = "$context.integration.error"
      integrationLatency = "$context.integration.latency"
    })
  }

  tags = {
    Environment = var.env_suffix
  }
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.access_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/POST/access"
}

resource "aws_api_gateway_usage_plan" "main" {
  name = "assine-usage-plan-${var.env_suffix}"

  api_stages {
    api_id = aws_api_gateway_rest_api.main.id
    stage  = aws_api_gateway_stage.main.stage_name
  }

  throttle_settings {
    rate_limit  = 1000
    burst_limit = 500
  }

  quota_settings {
    limit  = 10000
    period = "DAY"
  }
}

resource "aws_api_gateway_domain_name" "main" {
  count                    = var.domain_name != "" ? 1 : 0
  domain_name              = var.domain_name
  regional_certificate_arn = var.acm_cert_arn

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

resource "aws_api_gateway_base_path_mapping" "main" {
  count       = var.domain_name != "" ? 1 : 0
  api_id      = aws_api_gateway_rest_api.main.id
  stage_name  = aws_api_gateway_stage.main.stage_name
  domain_name = aws_api_gateway_domain_name.main[0].domain_name
}

resource "aws_iam_role" "api_gateway" {
  name = "assine-api-gateway-${var.env_suffix}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "apigateway.amazonaws.com"
      }
    }]
  })

  tags = {
    Name        = "assine-api-gateway-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_iam_role_policy" "api_gateway_lambda" {
  name = "api-gateway-lambda-invoke"
  role = aws_iam_role.api_gateway.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = var.access_function_arn
    }]
  })
}
