terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_lb" "main" {
  name                       = "${var.name}-alb-${var.env_suffix}"
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [var.sg_alb_id]
  subnets                    = var.subnet_ids
  drop_invalid_header_fields = true

  dynamic "access_logs" {
    for_each = var.access_logs_bucket_name != "" ? [var.access_logs_bucket_name] : []
    content {
      bucket  = access_logs.value
      enabled = true
      prefix  = "alb-logs"
    }
  }

  tags = {
    Name = "${var.name}-alb"
  }
}

# Listener HTTP 80: redirect para HTTPS. API Gateway + VPC Link v2 falam com
# o ALB via HTTPS no listener abaixo; este listener existe apenas como
# fallback defensivo (qualquer cliente interno que use 80 eh redirecionado).
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# Listener HTTPS 443: default eh 404. As regras por servico sao criadas pelo
# modulo ecs-service (path_pattern + forward ao target group). As regras de
# /healthz que existiam aqui (para health check do NLB) foram removidas
# junto com o modulo NLB -- API Gateway v2 integra diretamente com o ALB.
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\":\"Not Found\"}"
      status_code  = "404"
    }
  }
}
