terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_ecs_cluster" "main" {
  name = "${var.name}-${var.env_suffix}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Name = "${var.name}-${var.env_suffix}"
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = toset(var.services)

  name              = "/ecs/${var.env_suffix}/${each.value}"
  retention_in_days = 30

  tags = {
    Name = "/ecs/${var.env_suffix}/${each.value}"
  }
}

resource "aws_service_discovery_http_namespace" "main" {
  name        = "${var.name}.local"
  description = "Service Connect namespace for Assine services"

  tags = {
    Name = "${var.name}.local"
  }
}
