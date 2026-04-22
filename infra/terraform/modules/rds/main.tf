terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  # NAO renomeie este recurso: `name` eh usado como identificador fisico
  # pela AWS. Adicionar sufixo de env forca delete+recreate, que falha
  # enquanto o aws_db_instance existente aponta para ele.
  name       = "assine-db-subnet"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "assine-db-subnet-group"
  }
}

resource "aws_secretsmanager_secret" "master" {
  name                    = "assine/rds/master"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "master" {
  secret_id = aws_secretsmanager_secret.master.id
  secret_string = jsonencode({
    username = "assine_admin"
    password = random_password.master.result
    host     = try(aws_db_instance.main[0].address, "")
    port     = try(aws_db_instance.main[0].port, 5432)
    database = "postgres"
  })
}

# Nota: rotacao automatica de secret (aws_secretsmanager_secret_rotation)
# foi removida. A iteracao anterior apontava para a Lambda do SAR
# `aws-secretsmanager-rotation-single-user` por ARN hardcoded, mas essa
# Lambda NAO eh provisionada automaticamente na conta AWS -- quando ligada,
# falhava no apply. Se rotacao for necessaria, provisione a SAR stack ou
# use `aws_secretsmanager_secret_rotation` com uma Lambda propria.

resource "aws_db_instance" "main" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  # NAO adicione var.env_suffix aqui: `identifier` eh usado pela AWS como
  # chave fisica; renomear forca destroy+recreate da instancia (perda de
  # dados). A segregacao entre dev e prod ja eh feita por conta AWS.
  identifier     = "assine-db"
  engine         = "postgres"
  engine_version = "15"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "postgres"
  username = "assine_admin"
  password = random_password.master.result

  db_subnet_group_name   = try(aws_db_subnet_group.main[0].name, null)
  vpc_security_group_ids = [var.sg_rds_id]

  multi_az                = var.multi_az
  backup_retention_period = var.backup_retention_period
  deletion_protection     = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot

  # Performance Insights eh gratis em db.t4g.micro com retencao padrao de 7d.
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  enabled_cloudwatch_logs_exports = ["postgresql"]

  lifecycle {
    ignore_changes = [password]
  }

  tags = {
    Name = "assine-db-${var.env_suffix}"
  }
}

resource "aws_secretsmanager_secret" "app_users" {
  for_each = var.databases

  name                    = "assine/rds/${replace(each.key, "_", "-")}"
  recovery_window_in_days = 7
}

resource "random_password" "app_users" {
  for_each = var.databases
  length   = 32
  special  = false
}

resource "aws_secretsmanager_secret_version" "app_users" {
  for_each = var.databases

  secret_id = aws_secretsmanager_secret.app_users[each.key].id
  secret_string = jsonencode({
    username = each.value.user
    password = random_password.app_users[each.key].result
    host     = try(aws_db_instance.main[0].address, "")
    port     = try(aws_db_instance.main[0].port, 5432)
    database = each.value.name
    url      = "jdbc:postgresql://${try(aws_db_instance.main[0].address, "")}:${try(aws_db_instance.main[0].port, 5432)}/${each.value.name}"
  })
}

# A3: A instalacao das dependencias pip eh feita pelo CI (workflow deploy.yml)
# ou manualmente em maquinas locais antes do apply. Removemos o null_resource
# com local-exec para (1) nao depender de pip na maquina do terraform, (2)
# nao colidir com o passo equivalente do CI, (3) permitir apply off-line.
# Ver README secao "Lambda db_bootstrap".
data "archive_file" "db_bootstrap" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  type        = "zip"
  source_dir  = "${path.module}/files"
  output_path = "${path.module}/db_bootstrap.zip"
}

resource "aws_iam_role" "db_bootstrap" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  name = "assine-db-bootstrap-${var.env_suffix}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })

  tags = {
    Name = "assine-db-bootstrap"
  }
}

resource "aws_iam_role_policy" "db_bootstrap" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  name = "db-bootstrap-policy"
  role = aws_iam_role.db_bootstrap[0].name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = [
          aws_secretsmanager_secret.master.arn,
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
    ]
  })
}

resource "aws_iam_role_policy_attachment" "db_bootstrap_vpc" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  role       = aws_iam_role.db_bootstrap[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_lambda_function" "db_bootstrap" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  function_name = "assine-db-bootstrap-${var.env_suffix}"
  role          = aws_iam_role.db_bootstrap[0].arn
  handler       = "db_bootstrap.handler"
  runtime       = "python3.12"

  filename         = data.archive_file.db_bootstrap[0].output_path
  source_code_hash = data.archive_file.db_bootstrap[0].output_base64sha256

  vpc_config {
    subnet_ids         = var.lambda_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  timeout = 60

  tags = {
    Name = "assine-db-bootstrap"
  }

  depends_on = [
    aws_db_instance.main,
    aws_iam_role_policy.db_bootstrap,
    aws_iam_role_policy_attachment.db_bootstrap_vpc,
  ]
}

resource "aws_lambda_invocation" "db_init" {
  count = length(var.private_subnet_ids) > 0 ? 1 : 0

  function_name = aws_lambda_function.db_bootstrap[0].function_name

  input = jsonencode({
    Admin = {
      host     = aws_db_instance.main[0].address
      port     = aws_db_instance.main[0].port
      username = aws_db_instance.main[0].username
      password = random_password.master.result
    }
    Databases = { for k, v in var.databases : k => merge(v, { password = random_password.app_users[k].result }) }
  })

  lifecycle_scope = "CRUD"

  triggers = {
    db_config = sha1(jsonencode(var.databases))
    passwords = sha1(join(",", [for k in sort(keys(var.databases)) : random_password.app_users[k].result]))
  }
}
