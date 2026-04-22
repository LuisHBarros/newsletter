terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_cognito_user_pool" "main" {
  name = "assine-${var.env_suffix}"

  auto_verified_attributes = ["email"]
  username_attributes      = ["email"]

  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  password_policy {
    minimum_length                   = 12
    require_uppercase                = true
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 7
  }

  # Usa SES (DEVELOPER) apenas quando ha uma identidade em regiao suportada
  # por Cognito (us-east-1, us-west-2, eu-west-1). Caso contrario, cai no
  # envio padrao do Cognito (limitado a 50 emails/dia - suficiente p/ dev).
  # A regiao eh extraida do ARN: arn:aws:ses:<regiao>:<acct>:identity/<email>
  dynamic "email_configuration" {
    for_each = (
      var.use_ses_email &&
      var.ses_sender_identity_arn != "" &&
      contains(
        ["us-east-1", "us-west-2", "eu-west-1"],
        try(split(":", var.ses_sender_identity_arn)[3], "")
      )
    ) ? [1] : []
    content {
      email_sending_account = "DEVELOPER"
      from_email_address    = var.ses_sender_email
      source_arn            = var.ses_sender_identity_arn
    }
  }

  user_attribute_update_settings {
    attributes_require_verification_before_update = ["email"]
  }

  schema {
    name                = "email"
    attribute_data_type = "String"
    mutable             = true
    required            = true

    string_attribute_constraints {
      min_length = 0
      max_length = 2048
    }
  }

  schema {
    name                = "name"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 0
      max_length = 2048
    }
  }

  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  tags = {
    Name        = "assine-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  # Dominio Cognito (prefixo) eh globalmente unico em TODA a AWS; inclui um
  # sufixo derivado do account id para evitar colisoes em outras contas.
  domain       = var.account_id != "" ? "assine-${var.env_suffix}-${substr(var.account_id, 0, 6)}" : "assine-${var.env_suffix}"
  user_pool_id = aws_cognito_user_pool.main.id
}

resource "aws_cognito_resource_server" "api" {
  identifier = "assine-api"
  name       = "Assine API"

  user_pool_id = aws_cognito_user_pool.main.id

  scope {
    scope_name        = "billing:write"
    scope_description = "Write access to billing resources"
  }

  scope {
    scope_name        = "billing:admin"
    scope_description = "Admin access to billing resources"
  }

  scope {
    scope_name        = "content:read"
    scope_description = "Read access to content resources"
  }

  scope {
    scope_name        = "content:admin"
    scope_description = "Admin access to content resources"
  }

  scope {
    scope_name        = "subscriptions:write"
    scope_description = "Write access to subscriptions"
  }

  scope {
    scope_name        = "subscriptions:admin"
    scope_description = "Admin access to subscriptions"
  }
}

resource "aws_cognito_user_pool_client" "api_m2m" {
  name = "assine-api-m2m-${var.env_suffix}"

  user_pool_id = aws_cognito_user_pool.main.id

  supported_identity_providers = ["COGNITO"]

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes = [
    "assine-api/billing:write",
    "assine-api/billing:admin",
    "assine-api/content:read",
    "assine-api/content:admin",
    "assine-api/subscriptions:write",
    "assine-api/subscriptions:admin",
  ]

  generate_secret               = true
  prevent_user_existence_errors = "ENABLED"
  refresh_token_validity        = 1
  access_token_validity         = 1
  id_token_validity             = 1
  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  explicit_auth_flows = [
    "ALLOW_CUSTOM_AUTH",
  ]
}

resource "aws_cognito_user_pool_client" "api_web" {
  name = "assine-api-web-${var.env_suffix}"

  user_pool_id = aws_cognito_user_pool.main.id

  supported_identity_providers = ["COGNITO"]

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes = [
    "assine-api/billing:write",
    "assine-api/billing:admin",
    "assine-api/content:read",
    "assine-api/content:admin",
    "assine-api/subscriptions:write",
    "assine-api/subscriptions:admin",
    "openid",
    "profile",
    "email",
  ]

  generate_secret               = false
  prevent_user_existence_errors = "ENABLED"
  refresh_token_validity        = 30
  access_token_validity         = 1
  id_token_validity             = 1
  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  explicit_auth_flows = [
    "ALLOW_CUSTOM_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]
}

resource "aws_ssm_parameter" "cognito_issuer_uri" {
  name  = "/assine/cognito/issuer-uri"
  type  = "String"
  value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"

  tags = {
    Environment = var.env_suffix
  }
}
