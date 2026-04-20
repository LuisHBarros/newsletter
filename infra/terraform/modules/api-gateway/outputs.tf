output "api_gateway_id" {
  value = aws_api_gateway_rest_api.main.id
}

output "api_gateway_arn" {
  value = aws_api_gateway_rest_api.main.arn
}

output "api_gateway_url" {
  value = aws_api_gateway_stage.main.invoke_url
}

output "execution_arn" {
  value = aws_api_gateway_rest_api.main.execution_arn
}

output "vpc_link_id" {
  value = aws_api_gateway_vpc_link.main.id
}

output "stage_name" {
  value = aws_api_gateway_stage.main.stage_name
}

output "domain_name" {
  value = var.domain_name != "" ? aws_api_gateway_domain_name.main[0].domain_name : null
}
