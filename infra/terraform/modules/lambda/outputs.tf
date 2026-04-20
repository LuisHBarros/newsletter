output "access_function_arn" {
  value = aws_lambda_function.access.arn
}

output "access_function_invoke_arn" {
  value = aws_lambda_function.access.invoke_arn
}

output "access_function_name" {
  value = aws_lambda_function.access.function_name
}

output "notifications_function_arn" {
  value = aws_lambda_function.notifications.arn
}

output "notifications_function_name" {
  value = aws_lambda_function.notifications.function_name
}
