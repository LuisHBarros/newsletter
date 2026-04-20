terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

locals {
  queues = {
    "assine-events-${var.env_suffix}" = {
      fifo          = false
      content_dedup = false
    }
    "assine-subscriptions-${var.env_suffix}" = {
      fifo          = true
      content_dedup = false
    }
    "assine-content-jobs-${var.env_suffix}" = {
      fifo          = false
      content_dedup = false
    }
  }
}

resource "aws_sqs_queue" "dlq" {
  for_each = local.queues

  name                      = each.value.fifo ? "${each.key}-dlq.fifo" : "${each.key}-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true

  tags = {
    Name = "${each.key}-dlq"
  }
}

resource "aws_sqs_queue" "main" {
  for_each = local.queues

  name                    = each.value.fifo ? "${each.key}.fifo" : each.key
  sqs_managed_sse_enabled = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 5
  })

  tags = {
    Name = each.key
  }
}
