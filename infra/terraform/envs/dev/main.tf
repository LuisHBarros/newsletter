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
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  s3_use_path_style           = true

  endpoints {
    sqs            = "http://localhost:4566"
    secretsmanager = "http://localhost:4566"
    s3             = "http://localhost:4566"
    ec2            = "http://localhost:4566"
    rds            = "http://localhost:4566"
    iam            = "http://localhost:4566"
    dynamodb       = "http://localhost:4566"
    sts            = "http://localhost:4566"
    ecr            = "http://localhost:4566"
    elbv2          = "http://localhost:4566"
    ecs            = "http://localhost:4566"
    logs           = "http://localhost:4566"
    apigateway     = "http://localhost:4566"
    cognitoidp     = "http://localhost:4566"
    lambda         = "http://localhost:4566"
  }
}

module "sqs" {
  source     = "../../modules/sqs"
  env_suffix = var.env_suffix
}

module "rds" {
  source = "../../modules/rds"

  private_subnet_ids      = []
  sg_rds_id               = "sg-localstack-placeholder"
  backup_retention_period = 1
  deletion_protection     = false
  skip_final_snapshot     = true
  multi_az                = false
  env_suffix              = var.env_suffix
}

module "ecr" {
  source = "../../modules/ecr"

  repos  = ["billing", "content", "subscriptions", "notifications", "access"]
  prefix = "assine"
}

module "cognito" {
  source = "../../modules/cognito"

  env_suffix              = var.env_suffix
  aws_region              = "us-east-1"
  ses_sender_email        = "noreply@assine.local"
  ses_sender_identity_arn = "arn:aws:ses:us-east-1:000000000000:identity/assine.local"
}

module "vpc" {
  source               = "../../modules/vpc"
  enable_nat           = false
  enable_vpc_flow_logs = false
  enable_vpc_endpoints = false
  alb_logs_bucket_name = ""
  aws_region           = "us-east-1"
}
