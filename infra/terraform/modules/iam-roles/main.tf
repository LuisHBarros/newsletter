terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "secrets_access" {
  statement {
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
    ]
    resources = var.secrets_arns
  }

  dynamic "statement" {
    for_each = var.kms_key_arn != "" ? [1] : []

    content {
      effect = "Allow"
      actions = [
        "kms:Decrypt",
      ]
      resources = [var.kms_key_arn]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "assine-ecs-task-execution-${var.env_suffix}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json

  tags = {
    Name = "assine-ecs-task-execution"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_managed" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name   = "secrets-access"
  role   = aws_iam_role.ecs_task_execution.name
  policy = data.aws_iam_policy_document.secrets_access.json
}

resource "aws_iam_role" "ecs_task_role" {
  for_each = toset(["billing", "content", "subscriptions"])

  name               = "assine-task-role-${each.value}-${var.env_suffix}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json

  tags = {
    Name = "assine-task-role-${each.value}"
  }
}

data "aws_iam_policy_document" "billing_task" {
  statement {
    effect = "Allow"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
    ]
    resources = [
      var.sqs_arns.subscriptions,
      var.sqs_arns.events,
    ]
  }
}

data "aws_iam_policy_document" "subscriptions_task" {
  statement {
    effect = "Allow"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
    ]
    resources = [
      var.sqs_arns.subscriptions,
      var.sqs_arns.events,
    ]
  }
}

data "aws_iam_policy_document" "content_task" {
  statement {
    effect = "Allow"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
    ]
    resources = [
      var.sqs_arns.content_jobs,
      var.sqs_arns.events,
    ]
  }

  dynamic "statement" {
    for_each = var.content_bucket_arn != "" ? [1] : []

    content {
      effect = "Allow"
      actions = [
        "s3:PutObject",
        "s3:GetObject",
      ]
      resources = ["${var.content_bucket_arn}/*"]
    }
  }
}

resource "aws_iam_role_policy" "billing_task" {
  name   = "billing-task-policy"
  role   = aws_iam_role.ecs_task_role["billing"].name
  policy = data.aws_iam_policy_document.billing_task.json
}

resource "aws_iam_role_policy" "subscriptions_task" {
  name   = "subscriptions-task-policy"
  role   = aws_iam_role.ecs_task_role["subscriptions"].name
  policy = data.aws_iam_policy_document.subscriptions_task.json
}

resource "aws_iam_role_policy" "content_task" {
  name   = "content-task-policy"
  role   = aws_iam_role.ecs_task_role["content"].name
  policy = data.aws_iam_policy_document.content_task.json
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  for_each = toset(["notifications", "access"])

  name               = "assine-lambda-exec-${each.value}-${var.env_suffix}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = {
    Name = "assine-lambda-exec-${each.value}"
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  for_each = toset(["notifications", "access"])

  role       = aws_iam_role.lambda_exec[each.value].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  for_each = toset(["notifications", "access"])

  role       = aws_iam_role.lambda_exec[each.value].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "notifications_lambda" {
  statement {
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
    ]
    resources = [var.sqs_arns.events]
  }

  statement {
    effect = "Allow"
    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail",
    ]
    resources = ["arn:aws:ses:${var.aws_region}:${var.aws_account_id}:identity/${var.ses_sender_email}"]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.ses_sender_email]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "dynamodb:PutItem",
      "dynamodb:GetItem",
    ]
    resources = [var.dynamodb_table_arns.notifications]
  }
}

data "aws_iam_policy_document" "access_lambda" {
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]
    resources = [var.dynamodb_table_arns.access]
  }
}

resource "aws_iam_role_policy" "notifications_lambda" {
  name   = "notifications-lambda-policy"
  role   = aws_iam_role.lambda_exec["notifications"].name
  policy = data.aws_iam_policy_document.notifications_lambda.json
}

resource "aws_iam_role_policy" "access_lambda" {
  name   = "access-lambda-policy"
  role   = aws_iam_role.lambda_exec["access"].name
  policy = data.aws_iam_policy_document.access_lambda.json
}

data "aws_iam_policy_document" "github_oidc_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = ["arn:aws:iam::${var.aws_account_id}:oidc-provider/token.actions.githubusercontent.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repo}:ref:refs/heads/main",
        "repo:${var.github_repo}:pull_request",
      ]
    }
  }
}

data "aws_iam_policy_document" "github_actions" {
  statement {
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = var.ecr_repo_arns
  }

  statement {
    effect = "Allow"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "ecs:cluster"
      values   = [var.ecs_cluster_name != "" ? "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:cluster/${var.ecs_cluster_name}" : "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:cluster/*"]
    }
  }

  statement {
    effect = "Allow"
    actions = [
      "lambda:UpdateFunctionCode",
      "lambda:GetFunction",
    ]
    resources = [for name in var.lambda_function_names : "arn:aws:lambda:${var.aws_region}:${var.aws_account_id}:function:${name}"]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:PassRole",
    ]
    resources = concat(
      [aws_iam_role.ecs_task_execution.arn],
      [for role in aws_iam_role.ecs_task_role : role.arn]
    )
  }
}

resource "aws_iam_role" "github_actions_oidc" {
  name               = "assine-github-actions-oidc-${var.env_suffix}"
  assume_role_policy = data.aws_iam_policy_document.github_oidc_assume.json

  tags = {
    Name = "assine-github-actions-oidc"
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "github-actions-policy"
  role   = aws_iam_role.github_actions_oidc.name
  policy = data.aws_iam_policy_document.github_actions.json
}
