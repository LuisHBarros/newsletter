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

  # Container Insights adiciona metrics/logs custom que estouram o free tier
  # do CloudWatch. Usamos metrics padrao AWS/ECS (ver modulo monitoring).
  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = {
    Name = "${var.name}-${var.env_suffix}"
  }
}

# Attach FARGATE + FARGATE_SPOT capacity providers ao cluster. Permite que
# cada service escolha entre on-demand (FARGATE) e spot (FARGATE_SPOT) via
# capacity_provider_strategy. Nao definimos default aqui; o service define
# explicitamente.
resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = toset(var.services)

  name              = "/ecs/${var.env_suffix}/${each.value}"
  retention_in_days = var.log_retention_days

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
