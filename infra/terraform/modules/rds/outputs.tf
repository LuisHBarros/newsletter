output "endpoint" {
  value = try(aws_db_instance.main[0].endpoint, "")
}

output "port" {
  value = try(aws_db_instance.main[0].port, 5432)
}

output "instance_address" {
  value = try(aws_db_instance.main[0].address, "")
}

output "master_secret_arn" {
  value = aws_secretsmanager_secret.master.arn
}

output "app_secret_arns" {
  value = { for k, v in aws_secretsmanager_secret.app_users : k => v.arn }
}
