output "api_gateway_id" {
  value = aws_apigatewayv2_api.main.id
}

output "api_gateway_arn" {
  value = aws_apigatewayv2_api.main.arn
}

output "api_gateway_url" {
  value = aws_apigatewayv2_api.main.api_endpoint
}

output "execution_arn" {
  value = aws_apigatewayv2_api.main.execution_arn
}

output "vpc_link_id" {
  value = aws_apigatewayv2_vpc_link.main.id
}

output "stage_name" {
  value = aws_apigatewayv2_stage.main.name
}

output "domain_name" {
  value = var.domain_name != "" ? aws_apigatewayv2_domain_name.main[0].domain_name : null
}
