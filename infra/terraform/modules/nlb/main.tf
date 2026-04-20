terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

resource "aws_lb" "nlb" {
  name                             = "assine-nlb-${var.env_suffix}"
  internal                         = true
  load_balancer_type               = "network"
  subnets                          = var.private_subnet_ids
  enable_cross_zone_load_balancing = true

  tags = {
    Name        = "assine-nlb-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_lb_target_group" "alb" {
  name     = "assine-alb-tg-${var.env_suffix}"
  port     = 443
  protocol = "TLS"
  vpc_id   = var.vpc_id

  target_type = "alb"

  health_check {
    enabled  = true
    path     = "/healthz"
    port     = "443"
    protocol = "HTTPS"
    interval = 30
    timeout  = 5
  }

  tags = {
    Name        = "assine-alb-tg-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

resource "aws_lb_listener" "tcp" {
  load_balancer_arn = aws_lb.nlb.arn
  port              = 443
  protocol          = "TLS"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.alb.arn
  }
}

resource "aws_lb_target_group_attachment" "alb" {
  target_group_arn  = aws_lb_target_group.alb.arn
  target_id         = var.alb_arn
  port              = 443
  availability_zone = "all"
}
