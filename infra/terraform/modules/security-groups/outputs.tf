output "sg_alb_id" {
  value = aws_security_group.alb.id
}

output "sg_ecs_tasks_id" {
  value = aws_security_group.ecs_tasks.id
}

output "sg_rds_id" {
  value = aws_security_group.rds.id
}

output "sg_lambda_id" {
  value = aws_security_group.lambda.id
}
