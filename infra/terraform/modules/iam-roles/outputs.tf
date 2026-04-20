output "ecs_task_execution_role_arn" {
  value = aws_iam_role.ecs_task_execution.arn
}

output "ecs_task_role_arns" {
  value = { for k, v in aws_iam_role.ecs_task_role : k => v.arn }
}

output "lambda_exec_role_arns" {
  value = { for k, v in aws_iam_role.lambda_exec : k => v.arn }
}

output "github_actions_oidc_role_arn" {
  value = aws_iam_role.github_actions_oidc.arn
}
