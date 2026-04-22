terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}

# SNS Topic for alerts
resource "aws_sns_topic" "alerts" {
  name = "assine-alerts-${var.env_suffix}"
}

# Email subscription
resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# DLQ alarms (one per DLQ)
resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  for_each = toset(var.dlq_arns)

  alarm_name          = "assine-${var.env_suffix}-dlq-${each.key}-${each.value}-messages"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when DLQ has messages"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"
}

# ECS CPU alarms (one per service). Uses standard AWS/ECS metrics (free tier),
# not Container Insights ECS/ContainerInsights (extra $).
resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  for_each = toset(var.service_names)

  alarm_name          = "assine-${var.env_suffix}-ecs-${each.value}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Alert when ECS service CPU > 80%"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  datapoints_to_alarm = 2
  treat_missing_data  = "notBreaching"

  dimensions = {
    ServiceName = "assine-${each.value}-${var.env_suffix}"
    ClusterName = var.cluster_name
  }
}

# ALB 5xx error alarms (one per target group). CloudWatch AWS/ApplicationELB
# exige as dimensoes LoadBalancer=app/<name>/<id> e TargetGroup=targetgroup/<name>/<id>
# (ARN suffixes). Passar o ARN cru zera os data points silenciosamente.
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  for_each = var.target_group_arn_suffixes

  alarm_name          = "assine-${var.env_suffix}-${each.key}-alb-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Alert when ALB returns 5xx errors"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = each.value
  }
}
