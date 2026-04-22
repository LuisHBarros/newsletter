terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# HTTP API (API Gateway v2) + VPC Link v2 integrado diretamente com o ALB
# interno. Substitui o REST API + NLB + VPC Link v1 da iteracao anterior:
# - VPC Link v2 aceita ALB como target nativamente desde 2022 (nao precisa
#   de NLB intermediario).
# - HTTP API cobra ~70% menos por requisicao que REST API.
# - Cognito JWT authorizer eh nativo (sem Lambda authorizer).
resource "aws_apigatewayv2_api" "main" {
  name          = "assine-api-${var.env_suffix}"
  protocol_type = "HTTP"

  tags = {
    Name = "assine-api-${var.env_suffix}"
  }
}

resource "aws_apigatewayv2_vpc_link" "main" {
  name               = "assine-vpc-link-${var.env_suffix}"
  subnet_ids         = var.vpc_link_subnet_ids
  security_group_ids = [var.vpc_link_security_group_id]

  tags = {
    Name = "assine-vpc-link-${var.env_suffix}"
  }
}

resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.main.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-jwt"

  jwt_configuration {
    audience = var.cognito_client_ids
    issuer   = "https://cognito-idp.${var.aws_region}.amazonaws.com/${var.cognito_user_pool_id}"
  }
}

# Integracao principal: qualquer rota nao especifica vai para o ALB via VPC
# Link v2. TLS encerra no ALB (certificate ACM). Connection_type VPC_LINK
# com integration_method ANY preserva verbo/path do request.
resource "aws_apigatewayv2_integration" "alb" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "HTTP_PROXY"
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.main.id
  integration_method     = "ANY"
  integration_uri        = var.alb_listener_arn
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:header.X-Forwarded-Prefix" = "/"
  }
}

resource "aws_apigatewayv2_integration" "access_lambda" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = var.access_function_invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

# Rota especifica para o Lambda de access ANTES do catch-all. API Gateway v2
# faz match por especificidade: "POST /access" > "ANY /{proxy+}".
resource "aws_apigatewayv2_route" "access_post" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /access"
  target    = "integrations/${aws_apigatewayv2_integration.access_lambda.id}"

  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_route" "catch_all" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.alb.id}"

  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_stage" "main" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  tags = {
    Environment = var.env_suffix
  }
}

resource "aws_lambda_permission" "access_invoke" {
  statement_id  = "AllowAPIGatewayV2Invoke"
  action        = "lambda:InvokeFunction"
  function_name = var.access_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/POST/access"
}

resource "aws_apigatewayv2_domain_name" "main" {
  count       = var.domain_name != "" ? 1 : 0
  domain_name = var.domain_name

  domain_name_configuration {
    certificate_arn = var.acm_cert_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }
}

resource "aws_apigatewayv2_api_mapping" "main" {
  count       = var.domain_name != "" ? 1 : 0
  api_id      = aws_apigatewayv2_api.main.id
  domain_name = aws_apigatewayv2_domain_name.main[0].id
  stage       = aws_apigatewayv2_stage.main.id
}
