output "nlb_dns" {
  value = aws_lb.nlb.dns_name
}

output "nlb_arn" {
  value = aws_lb.nlb.arn
}

output "nlb_zone_id" {
  value = aws_lb.nlb.zone_id
}
