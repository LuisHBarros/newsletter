output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "vpc_cidr" {
  value = aws_vpc.main.cidr_block
}

output "igw_id" {
  value = aws_internet_gateway.main.id
}

output "nat_gateway_ids" {
  value = aws_nat_gateway.main[*].id
}

output "nat_eip_ids" {
  value = aws_eip.nat[*].id
}

output "vpc_flow_log_id" {
  value = try(aws_flow_log.main[0].id, null)
}

output "vpc_endpoints" {
  value = {
    s3              = try(aws_vpc_endpoint.s3[0].id, null)
    ecr_api         = try(aws_vpc_endpoint.ecr_api[0].id, null)
    ecr_dkr         = try(aws_vpc_endpoint.ecr_dkr[0].id, null)
    secretsmanager  = try(aws_vpc_endpoint.secretsmanager[0].id, null)
    sqs             = try(aws_vpc_endpoint.sqs[0].id, null)
    cloudwatch_logs = try(aws_vpc_endpoint.cloudwatch_logs[0].id, null)
  }
}

output "alb_logs_bucket_name" {
  value = try(aws_s3_bucket.alb_logs[0].id, null)
}

output "vpc_endpoint_security_group_id" {
  value = try(aws_security_group.vpc_endpoints[0].id, null)
}
