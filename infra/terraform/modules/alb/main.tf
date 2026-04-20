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

resource "aws_lb_listener_rule" "healthz" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 1

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "ok"
      status_code  = "200"
    }
  }

  condition {
    path_pattern {
      values = ["/healthz"]
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-2021-06"
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

resource "aws_lb_listener_rule" "healthz_https" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "ok"
      status_code  = "200"
    }
  }

  condition {
    path_pattern {
      values = ["/healthz"]
    }
  }
}
