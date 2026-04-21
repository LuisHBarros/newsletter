terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_ecs_task_definition" "this" {
  family                   = "assine-${var.name}-${var.env_suffix}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.task_execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode(
    concat(
      [
        {
          name      = var.name
          image     = var.image
          essential = true

          portMappings = [
            {
              containerPort = var.container_port
              hostPort      = var.container_port
              protocol      = "tcp"
            }
          ]

          environment = var.environment
          secrets     = var.secrets

          logConfiguration = {
            logDriver = "awslogs"
            options = {
              awslogs-group         = var.log_group_name
              awslogs-region        = var.aws_region
              awslogs-stream-prefix = var.name
            }
          }
        }
      ],
      var.enable_otel_sidecar ? [
        {
          name      = "aws-otel-collector"
          image     = "public.ecr.aws/aws-observability/aws-otel-collector:latest"
          essential = false
          environment = [
            { name = "OTEL_TRACES_EXPORTER", value = "otlp" },
            { name = "OTEL_EXPORTER_OTLP_PROTOCOL", value = "http/protobuf" },
            { name = "OTEL_RESOURCE_ATTRIBUTES", value = "service.name=assine-${var.name}" },
          ]
        }
      ] : []
    )
  )

  lifecycle {
    # A imagem do container eh atualizada fora do Terraform pelo pipeline de CI
    # (register-task-definition apontando para a tag SHA). Ignorar aqui evita
    # que `terraform apply` reverta para a tag :latest declarada em var.image.
    ignore_changes = [container_definitions]
  }
}

resource "aws_lb_target_group" "this" {
  name        = "assine-${var.name}-${var.env_suffix}"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path     = "/actuator/health"
    port     = tostring(var.container_port)
    protocol = "HTTP"
    matcher  = "200"
    interval = 30
    timeout  = 5
  }

  tags = {
    Name = "assine-${var.name}-tg"
  }
}

resource "aws_lb_listener_rule" "this" {
  listener_arn = var.listener_arn
  priority     = var.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  condition {
    path_pattern {
      values = var.path_patterns
    }
  }
}

resource "aws_ecs_service" "this" {
  name                               = "assine-${var.name}-${var.env_suffix}"
  cluster                            = var.cluster_arn
  task_definition                    = aws_ecs_task_definition.this.arn
  desired_count                      = var.desired_count
  launch_type                        = "FARGATE"
  health_check_grace_period_seconds  = 60
  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  network_configuration {
    subnets         = var.subnet_ids
    security_groups = var.security_group_ids
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = var.name
    container_port   = var.container_port
  }

  deployment_controller {
    type = "ECS"
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  tags = {
    Name = "assine-${var.name}"
  }

  lifecycle {
    # O pipeline de CI registra novas revisoes de task definition com a tag SHA
    # e aponta o servico para ela. Ignorar aqui preserva esse estado.
    ignore_changes = [task_definition, desired_count]
  }
}
