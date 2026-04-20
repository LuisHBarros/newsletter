output "cluster_arn" {
  value = aws_ecs_cluster.main.arn
}

output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "log_group_names" {
  value = { for k, v in aws_cloudwatch_log_group.service : k => v.name }
}

output "namespace_arn" {
  value = aws_service_discovery_http_namespace.main.arn
}
