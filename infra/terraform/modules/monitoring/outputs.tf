output "topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "alarm_arns" {
  value = {
    dlq     = values(aws_cloudwatch_metric_alarm.dlq_messages)[*].arn
    ecs_cpu = values(aws_cloudwatch_metric_alarm.ecs_cpu)[*].arn
    alb_5xx = values(aws_cloudwatch_metric_alarm.alb_5xx)[*].arn
  }
}
