# Assine - Terraform Infrastructure

Modular Terraform para VPC, RDS PostgreSQL multi-db, SQS, ECR, ECS Fargate, ALB e IAM roles, com paridade LocalStack (dev) / AWS (prod).

## Estrutura

```
infra/terraform/
  bootstrap/          # Cria bucket tfstate + lock table (run once, local state)
  modules/
    vpc/              # VPC, subnets pub/priv, IGW, NAT, route tables
    security-groups/  # SGs: ALB, ECS tasks, RDS, Lambda
    rds/              # Instancia unica + multiplos databases + users (postgresql provider)
    sqs/              # Filas standard e FIFO + DLQs + redrive
    ecr/              # ECR repos com lifecycle policy (keep last 10 tagged, expire untagged 7d)
    iam-roles/        # ECS task execution/role, Lambda exec, GitHub Actions OIDC
    alb/              # ALB + HTTPS listener + ACM cert + path-based routing
    ecs-cluster/      # ECS Fargate cluster + CloudWatch log groups + Service Discovery
    ecs-service/      # Reusable task def + service + TG + listener rule
  envs/
    dev/              # LocalStack (endpoint http://localhost:4566) - ECR only
    prod/             # AWS real (us-east-1) - full stack
```

## Pre-requisitos

- Terraform >= 1.5
- AWS CLI configurado (para prod)
- Docker + Docker Compose (para dev com LocalStack)

## Bootstrap (rodar 1x em prod)

Cria o S3 bucket para tfstate e DynamoDB table para locking:

```bash
cd infra/terraform/bootstrap
terraform init
terraform plan
terraform apply
```

Outputs: `assine-tfstate` (bucket) e `assine-tfstate-lock` (DynamoDB table).

Dev nao precisa de bootstrap — usa backend local.

## Dev (LocalStack)

1. Suba o LocalStack:

```bash
docker compose up -d localstack
```

2. Aplique o Terraform:

```bash
cd infra/terraform/envs/dev
terraform init
terraform plan
terraform apply
```

Dev instancia SQS + RDS simplificado + ECR (para testes locais de push).
Nao instancia ECS Fargate (nao suportado no LocalStack Community).
Backend local (`terraform.tfstate`).

## Prod (AWS real)

1. Certifique-se que o bootstrap ja foi executado.
2. Configure AWS credentials com acesso adequado.
3. Edite `terraform.tfvars` com o ACM certificate ARN e GitHub repo.
4. Aplique o Terraform:

```bash
cd infra/terraform/envs/prod
terraform init
terraform plan
terraform apply
```

Prod instancia tudo: VPC -> Security Groups -> RDS -> SQS -> ECR -> IAM -> ALB -> ECS Cluster -> ECS Services.
Backend remoto S3 + DynamoDB lock.

## Deploy ECS

### Roteamento ALB

| Path | Service | Priority |
|------|---------|----------|
| `/subscriptions/*`, `/plans/*` | subscriptions | 10 |
| `/billing/*`, `/payments/*` | billing | 20 |
| `/content/*`, `/webhooks/notion` | content | 30 |

### Task Definition

- CPU: 512, Memoria: 1024 MiB
- Runtime: LINUX/X86_64 (Java 21 Spring Boot)
- Health check: `/actuator/health` na porta 8080
- Log driver: `awslogs` -> CloudWatch (retencao 30d)
- Circuit breaker: habilitado com rollback automatico

### ECR Repositories

| Repo | Servico |
|------|---------|
| assine/billing | Billing (Java) |
| assine/content | Content (Java) |
| assine/subscriptions | Subscriptions (Java) |
| assine/notifications | Notifications (Go Lambda) |
| assine/access | Access (Go Lambda) |

Lifecycle: manter ultimas 10 tagged `v*`, expirar untagged > 7 dias.

## Setup OIDC (GitHub Actions)

O modulo `iam-roles` cria automaticamente a role `assine-github-actions-oidc` com trust no GitHub OIDC provider. Para que funcione, voce precisa criar o Identity Provider na AWS (se ainda nao existe):

### 1. Criar GitHub OIDC Provider

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faaad973343d1e0c0aeldbe0d
```

### 2. Configurar GitHub Repository Variables

No repo GitHub, em Settings > Variables and secrets > Actions:

| Variable | Valor |
|----------|-------|
| `AWS_REGION` | `us-east-1` |
| `AWS_ACCOUNT_ID` | `<seu account ID>` |
| `AWS_OIDC_ROLE_ARN` | Output do terraform: `github_actions_oidc_role_arn` |

### 3. Secrets necessarios no Secrets Manager

Antes do deploy, crie manualmente:
- `assine/stripe` - Stripe API key (usado pelo billing)
- `assine/cognito/issuer-uri` - Cognito issuer URI (Sprint 4)

Os secrets RDS sao criados automaticamente pelo modulo `rds`.

## Modulos

### VPC
- CIDR 10.0.0.0/16, 2 AZs
- Subnets publicas: 10.0.0.0/24, 10.0.1.0/24
- Subnets privadas: 10.0.10.0/24, 10.0.11.0/24
- NAT Gateway habilitado via `enable_nat = true` (prod only)

### Security Groups
- `sg_alb`: ingress 80/443 from 0.0.0.0/0
- `sg_ecs_tasks`: ingress 8080 from sg_alb
- `sg_rds`: ingress 5432 from sg_ecs_tasks + sg_lambda
- `sg_lambda`: egress all

### RDS
- PostgreSQL 15, db.t4g.micro, 20GB gp3, encrypted
- 3 databases: newsletter_subscriptions, newsletter_billing, newsletter_content
- Users isolados: subscriptions_app, billing_app, content_app
- Senhas em Secrets Manager: `assine/rds/<service>`

### SQS
| Fila | Tipo | DLQ |
|------|------|-----|
| assine-events-{env} | standard | assine-events-{env}-dlq |
| assine-subscriptions-{env}.fifo | FIFO | assine-subscriptions-{env}-dlq.fifo |
| assine-content-jobs-{env} | standard | assine-content-jobs-{env}-dlq |

Redrive: maxReceiveCount = 5

### ECR
- 5 repos: billing, content, subscriptions, notifications, access
- Image scanning on push habilitado
- Tag mutability: IMMUTABLE
- Lifecycle: keep last 10 `v*` tagged, expire untagged > 7d

### IAM Roles
- `ecs_task_execution`: AmazonECSTaskExecutionRolePolicy + secretsmanager:GetSecretValue
- `ecs_task_role` (per-service): SQS permissions minimas
- `lambda_exec` (notifications/access): logs + DynamoDB + SES/SQS
- `github_actions_oidc`: ECR push + ECS update + Lambda update + iam:PassRole

### ALB
- Public ALB em subnets publicas
- HTTP 80 -> redirect HTTPS 443
- HTTPS 443 com ACM cert, default 404
- Path-based routing para 3 ECS services

### ECS Cluster
- Cluster `assine-<env>` com Container Insights ON
- CloudWatch log groups `/ecs/<env>/<service>` retencao 30d
- Service Connect namespace `assine.local`

## Validacao

```bash
terraform fmt -recursive infra/terraform/
terraform -chdir=infra/terraform/envs/dev validate
terraform -chdir=infra/terraform/envs/prod validate
```

## Fora do escopo (sprints seguintes)
- Auto Scaling policies
- WAF no ALB
- Canary/Blue-green (CodeDeploy)
- DynamoDB tables, S3 buckets (sprint futuro)
- Cognito User Pool (Sprint 4)
- Fiscal Lambda (Sprint 3)
