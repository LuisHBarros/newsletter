output "alb_arn" {
  value = aws_lb.main.arn
}

output "alb_arn_suffix" {
  value       = aws_lb.main.arn_suffix
  description = "ARN suffix (app/<name>/<id>) for CloudWatch LoadBalancer dimension"
}

output "listener_arn" {
  value = aws_lb_listener.https.arn
}

output "dns_name" {
  value = aws_lb.main.dns_name
}

output "zone_id" {
  value = aws_lb.main.zone_id
}
