output "user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  value = aws_cognito_user_pool.main.arn
}

output "user_pool_endpoint" {
  value = aws_cognito_user_pool.main.endpoint
}

output "client_id_m2m" {
  value = aws_cognito_user_pool_client.api_m2m.id
}

output "client_secret_m2m" {
  value     = aws_cognito_user_pool_client.api_m2m.client_secret
  sensitive = true
}

output "client_id_web" {
  value = aws_cognito_user_pool_client.api_web.id
}

output "client_id" {
  value = aws_cognito_user_pool_client.api_web.id
}

output "client_secret" {
  value     = aws_cognito_user_pool_client.api_m2m.client_secret
  sensitive = true
}

output "issuer_uri" {
  value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "domain" {
  value = aws_cognito_user_pool_domain.main.domain
}
