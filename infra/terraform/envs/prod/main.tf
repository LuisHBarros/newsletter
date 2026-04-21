terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

data "aws_caller_identity" "current" {}

module "vpc" {
  source = "../../modules/vpc"

  enable_nat                   = true
  enable_vpc_flow_logs         = true
  vpc_flow_logs_retention_days = 30
  enable_vpc_endpoints         = true
  alb_logs_bucket_name         = "assine-alb-logs-prod"
  alb_logs_retention_days      = 30
  aws_region                   = "us-east-1"
}

module "security_groups" {
  source = "../../modules/security-groups"

  vpc_id   = module.vpc.vpc_id
  vpc_cidr = module.vpc.vpc_cidr
}

module "rds" {
  source = "../../modules/rds"

  private_subnet_ids = module.vpc.private_subnet_ids
  sg_rds_id          = module.security_groups.sg_rds_id
  # Free-tier: automated backups nao sao permitidos em db.t4g.micro.
  # Rotacao de secret depende do Lambda do SAR que nao esta instalado.
  backup_retention_period  = 0
  deletion_protection      = true
  skip_final_snapshot      = false
  env_suffix               = var.env_suffix
  lambda_subnet_ids        = module.vpc.private_subnet_ids
  lambda_security_group_id = module.security_groups.sg_lambda_id
  multi_az                 = false
  enable_secret_rotation   = false
  secret_rotation_days     = 30
  aws_region               = "us-east-1"
  aws_account_id           = data.aws_caller_identity.current.account_id
}

module "sqs" {
  source     = "../../modules/sqs"
  env_suffix = var.env_suffix
}

module "ecr" {
  source = "../../modules/ecr"

  repos = ["billing", "content", "subscriptions", "notifications", "access"]
}

resource "aws_secretsmanager_secret" "stripe" {
  name                    = "assine/stripe"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "stripe" {
  secret_id     = aws_secretsmanager_secret.stripe.id
  secret_string = jsonencode({ api_key = "placeholder" })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

module "iam" {
  source = "../../modules/iam-roles"

  env_suffix     = var.env_suffix
  aws_region     = "us-east-1"
  aws_account_id = data.aws_caller_identity.current.account_id
  github_repo    = var.github_repo
  secrets_arns = concat(
    values(module.rds.app_secret_arns),
    [
      aws_secretsmanager_secret.stripe.arn
    ]
  )
  kms_key_arn      = ""
  ecr_repo_arns    = values(module.ecr.repository_arns)
  ecs_cluster_name = module.ecs_cluster.cluster_name
  # Literais (nao referenciar module.lambda.*) para quebrar a dependencia
  # iam -> lambda. Com a referencia, `terraform apply -target=module.iam` no
  # bootstrap arrastava module.lambda para o plano, e a Lambda exige que a
  # imagem :latest ja exista no ECR -- o que so acontece apos build-lambda-go.
  # Os nomes abaixo sao os mesmos definidos em modules/lambda/main.tf.
  lambda_function_names = [
    "assine-access-${var.env_suffix}",
    "assine-notifications-${var.env_suffix}",
  ]

  sqs_arns = {
    events        = module.sqs.queues["assine-events-${var.env_suffix}"].arn
    subscriptions = module.sqs.queues["assine-subscriptions-${var.env_suffix}"].arn
    content_jobs  = module.sqs.queues["assine-content-jobs-${var.env_suffix}"].arn
  }

  dynamodb_table_arns = {
    notifications = "arn:aws:dynamodb:us-east-1:${data.aws_caller_identity.current.account_id}:table/processed_events"
    access        = "arn:aws:dynamodb:us-east-1:${data.aws_caller_identity.current.account_id}:table/content-permissions"
  }

  ses_sender_email = var.ses_sender_email
}

module "alb" {
  source = "../../modules/alb"

  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  sg_alb_id               = module.security_groups.sg_alb_id
  certificate_arn         = var.acm_cert_arn
  env_suffix              = var.env_suffix
  access_logs_bucket_name = ""
}

module "ecs_cluster" {
  source = "../../modules/ecs-cluster"

  env_suffix = var.env_suffix
  services   = ["billing", "content", "subscriptions"]
}

module "cognito" {
  source = "../../modules/cognito"

  env_suffix              = var.env_suffix
  aws_region              = "us-east-1"
  use_ses_email           = false
  ses_sender_email        = var.ses_sender_email
  ses_sender_identity_arn = var.ses_sender_identity_arn
}

module "nlb" {
  source = "../../modules/nlb"

  vpc_id             = module.vpc.vpc_id
  env_suffix         = var.env_suffix
  private_subnet_ids = module.vpc.private_subnet_ids
  alb_arn            = module.alb.alb_arn
  alb_listener_arn   = module.alb.listener_arn
  certificate_arn    = var.acm_cert_arn
}

module "lambda" {
  source = "../../modules/lambda"

  env_suffix             = var.env_suffix
  aws_region             = "us-east-1"
  aws_account_id         = data.aws_caller_identity.current.account_id
  ecr_repository_urls    = module.ecr.repository_urls
  access_role_arn        = module.iam.lambda_exec_role_arns["access"]
  notifications_role_arn = module.iam.lambda_exec_role_arns["notifications"]
  subnet_ids             = module.vpc.private_subnet_ids
  security_group_ids     = [module.security_groups.sg_ecs_tasks_id]
  sqs_event_queue_arn    = module.sqs.queues["assine-events-${var.env_suffix}"].arn
  ses_sender_email       = var.ses_sender_email
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/assine-api-${var.env_suffix}"
  retention_in_days = 30

  tags = {
    Name        = "/aws/apigateway/assine-api-${var.env_suffix}"
    Environment = var.env_suffix
  }
}

module "api_gateway" {
  source = "../../modules/api-gateway"

  env_suffix                 = var.env_suffix
  aws_region                 = "us-east-1"
  nlb_dns                    = module.nlb.nlb_dns
  nlb_arn                    = module.nlb.nlb_arn
  cognito_user_pool_arn      = module.cognito.user_pool_arn
  access_function_invoke_arn = module.lambda.access_function_invoke_arn
  access_function_arn        = module.lambda.access_function_arn
  access_function_name       = module.lambda.access_function_name
  api_gateway_log_group_arn  = aws_cloudwatch_log_group.api_gateway.arn
  domain_name                = var.api_domain_name
  acm_cert_arn               = var.acm_cert_arn
  openapi_body               = ""
  openapi_spec_path          = "${path.module}/../../../../docs/api/openapi.yaml"

  depends_on = [module.cognito, module.nlb]
}

module "svc_subscriptions" {
  source = "../../modules/ecs-service"

  name                    = "subscriptions"
  env_suffix              = var.env_suffix
  cluster_arn             = module.ecs_cluster.cluster_arn
  image                   = "${module.ecr.repository_urls["subscriptions"]}:latest"
  vpc_id                  = module.vpc.vpc_id
  cpu                     = 512
  memory                  = 1024
  container_port          = 8080
  desired_count           = 2
  subnet_ids              = module.vpc.private_subnet_ids
  security_group_ids      = [module.security_groups.sg_ecs_tasks_id]
  task_execution_role_arn = module.iam.ecs_task_execution_role_arn
  task_role_arn           = module.iam.ecs_task_role_arns["subscriptions"]
  listener_arn            = module.alb.listener_arn
  path_patterns           = ["/subscriptions/*", "/plans/*"]
  priority                = 10
  log_group_name          = module.ecs_cluster.log_group_names["subscriptions"]
  enable_otel_sidecar     = true

  environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "AWS_REGION", value = "us-east-1" },
    { name = "SERVER_PORT", value = "8080" },
    { name = "COGNITO_ISSUER_URI", value = module.cognito.issuer_uri },
  ]

  secrets = [
    { name = "AWS_RDS_URL", valueFrom = "${module.rds.app_secret_arns["subscriptions"]}:url::" },
    { name = "AWS_RDS_USERNAME", valueFrom = "${module.rds.app_secret_arns["subscriptions"]}:username::" },
    { name = "AWS_RDS_PASSWORD", valueFrom = "${module.rds.app_secret_arns["subscriptions"]}:password::" },
  ]
}

module "svc_billing" {
  source = "../../modules/ecs-service"

  name                    = "billing"
  env_suffix              = var.env_suffix
  cluster_arn             = module.ecs_cluster.cluster_arn
  image                   = "${module.ecr.repository_urls["billing"]}:latest"
  vpc_id                  = module.vpc.vpc_id
  cpu                     = 512
  memory                  = 1024
  container_port          = 8080
  desired_count           = 2
  subnet_ids              = module.vpc.private_subnet_ids
  security_group_ids      = [module.security_groups.sg_ecs_tasks_id]
  task_execution_role_arn = module.iam.ecs_task_execution_role_arn
  task_role_arn           = module.iam.ecs_task_role_arns["billing"]
  listener_arn            = module.alb.listener_arn
  path_patterns           = ["/billing/*", "/payments/*"]
  priority                = 20
  log_group_name          = module.ecs_cluster.log_group_names["billing"]
  enable_otel_sidecar     = true

  environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "AWS_REGION", value = "us-east-1" },
    { name = "SERVER_PORT", value = "8080" },
    { name = "COGNITO_ISSUER_URI", value = module.cognito.issuer_uri },
  ]

  secrets = [
    { name = "AWS_RDS_URL", valueFrom = "${module.rds.app_secret_arns["billing"]}:url::" },
    { name = "AWS_RDS_USERNAME", valueFrom = "${module.rds.app_secret_arns["billing"]}:username::" },
    { name = "AWS_RDS_PASSWORD", valueFrom = "${module.rds.app_secret_arns["billing"]}:password::" },
    { name = "STRIPE_API_KEY", valueFrom = "${aws_secretsmanager_secret.stripe.arn}:api_key::" },
  ]
}

module "svc_content" {
  source = "../../modules/ecs-service"

  name                    = "content"
  env_suffix              = var.env_suffix
  cluster_arn             = module.ecs_cluster.cluster_arn
  image                   = "${module.ecr.repository_urls["content"]}:latest"
  vpc_id                  = module.vpc.vpc_id
  cpu                     = 512
  memory                  = 1024
  container_port          = 8080
  desired_count           = 2
  subnet_ids              = module.vpc.private_subnet_ids
  security_group_ids      = [module.security_groups.sg_ecs_tasks_id]
  task_execution_role_arn = module.iam.ecs_task_execution_role_arn
  task_role_arn           = module.iam.ecs_task_role_arns["content"]
  listener_arn            = module.alb.listener_arn
  path_patterns           = ["/content/*", "/webhooks/notion"]
  priority                = 30
  log_group_name          = module.ecs_cluster.log_group_names["content"]
  enable_otel_sidecar     = true

  environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "AWS_REGION", value = "us-east-1" },
    { name = "SERVER_PORT", value = "8080" },
    { name = "COGNITO_ISSUER_URI", value = module.cognito.issuer_uri },
  ]

  secrets = [
    { name = "AWS_RDS_URL", valueFrom = "${module.rds.app_secret_arns["content"]}:url::" },
    { name = "AWS_RDS_USERNAME", valueFrom = "${module.rds.app_secret_arns["content"]}:username::" },
    { name = "AWS_RDS_PASSWORD", valueFrom = "${module.rds.app_secret_arns["content"]}:password::" },
  ]
}

module "monitoring" {
  source        = "../../modules/monitoring"
  env_suffix    = var.env_suffix
  alert_email   = var.alert_email
  service_names = ["billing", "content", "subscriptions"]
  dlq_arns      = [for q in module.sqs.queues : q.dlq_arn]
  cluster_name  = module.ecs_cluster.cluster_name
  # Map com chaves estaticas (conhecidas no plan) para permitir for_each no
  # modulo de monitoring mesmo antes dos target groups serem criados.
  target_group_arns = {
    billing       = module.svc_billing.target_group_arn
    content       = module.svc_content.target_group_arn
    subscriptions = module.svc_subscriptions.target_group_arn
  }
}

output "alb_dns" {
  value = module.alb.dns_name
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "ecs_task_role_arns" {
  value = module.iam.ecs_task_role_arns
}

output "github_actions_oidc_role_arn" {
  value = module.iam.github_actions_oidc_role_arn
}

output "ecs_cluster_name" {
  value = module.ecs_cluster.cluster_name
}

output "api_gateway_url" {
  value = module.api_gateway.api_gateway_url
}

output "cognito_user_pool_id" {
  value = module.cognito.user_pool_id
}

output "cognito_client_id_m2m" {
  value = module.cognito.client_id_m2m
}

output "cognito_client_secret_m2m" {
  value     = module.cognito.client_secret_m2m
  sensitive = true
}

output "cognito_client_id_web" {
  value = module.cognito.client_id_web
}

output "cognito_issuer_uri" {
  value = module.cognito.issuer_uri
}
