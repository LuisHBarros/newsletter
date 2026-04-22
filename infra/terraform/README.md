# Assine - Terraform Infrastructure

Terraform modular para rodar a plataforma Assine em AWS dentro do **Free Tier + ~$200/mes de budget**. Mantém 3 serviços Fargate (billing, content, subscriptions) + Lambdas (access, notifications) + API Gateway HTTP API v2 com VPC Link v2 direto ao ALB interno, RDS PostgreSQL `db.t4g.micro` e SQS.

## Estrutura

```
infra/terraform/
  bootstrap/          # Cria bucket tfstate + lock table (run once, local state)
  modules/
    vpc/              # VPC, subnets pub/priv, IGW. NAT/VPC endpoints/Flow Logs OFF em prod
    security-groups/  # SGs: ALB, ECS tasks, RDS, Lambda
    rds/              # db.t4g.micro + multiplos databases + users via Lambda bootstrap
    sqs/              # Filas standard e FIFO + DLQs + redrive
    ecr/              # ECR repos com lifecycle (keep last 10 tagged, expire untagged 7d)
    iam-roles/        # ECS exec/task, Lambda exec, GitHub OIDC (trust = main + tags v*)
    alb/              # ALB interno + HTTPS listener + ACM cert + path-based routing
    ecs-cluster/      # ECS cluster (Container Insights OFF) + FARGATE/FARGATE_SPOT capacity providers + log groups 7d
    ecs-service/      # Task def + service (FARGATE_SPOT opcional + assign_public_ip) + TG + listener rule
    api-gateway/      # API Gateway HTTP API v2 + VPC Link v2 -> ALB + Cognito JWT authorizer + Lambda access
    cognito/          # User Pool + m2m/web clients + dominio globalmente unico (account_id-suffix)
    lambda/           # access + notifications (container image, arm64)
    monitoring/       # SNS + CloudWatch alarms (DLQ, ECS CPU, ALB 5xx) usando dimensoes corretas
  envs/
    dev/              # LocalStack (endpoint http://localhost:4566) - subset de recursos
    prod/             # AWS real (us-east-1) - stack completa Free Tier + $200 budget
```

### Changes in the Free-Tier iteration

| # | Mudanca | Efeito |
|---|---------|--------|
| C1 | `enable_nat = false`, tasks em subnets publicas com `assign_public_ip = true` | -$64/mo |
| C2 | `module.nlb` removido; API Gateway HTTP API v2 + VPC Link v2 direto ao ALB | -$16/mo + REST->HTTP ~70% menos |
| C3 | `enable_vpc_endpoints = false` | -$36/mo |
| C4 | `enable_otel_sidecar = false` | -$15 a -$25/mo |
| C5 | `desired_count = 1` por servico | -50% custo Fargate |
| C6 | `use_fargate_spot = true` (capacity provider) | -70% no custo dos 3 servicos |
| C7 | Container Insights OFF | -$5 a -$15/mo |
| C8 | VPC Flow Logs OFF | -$3 a -$10/mo |
| C9 | Log retention reduzida para 7d; API GW access logs removidos | -$2 a -$5/mo |
| C10 | RDS Performance Insights mantido com retencao 7d (gratis) | $0 |
| C11 | ALB access logs desabilitados (`alb_logs_bucket_name = ""`) | -$2/mo |

**Custo estimado pos-mudancas: ~$35-41/mo** (era ~$221/mo). Bem dentro do budget.

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
- ALB **interno** em subnets privadas (nao eh acessivel externamente)
- Ingress: API Gateway v2 via VPC Link v2 (sem NLB intermediario)
- HTTP 80 -> redirect HTTPS 443 (fallback defensivo)
- HTTPS 443 com ACM cert, default 404
- Path-based routing para 3 ECS services
- Access logs desabilitados por padrao (passe `alb_logs_bucket_name` para reativar)

### ECS Cluster
- Cluster `assine-<env>` com **Container Insights OFF** (free-tier)
- Capacity providers **FARGATE + FARGATE_SPOT** attachados; services escolhem via `use_fargate_spot`
- CloudWatch log groups `/ecs/<env>/<service>` com retencao configuravel (default 7d)
- Service Discovery namespace `assine.local`

### ECS Service
- `launch_type = FARGATE` OU `capacity_provider_strategy = FARGATE_SPOT` (mutuamente exclusivos)
- `assign_public_ip = true` obrigatorio quando rodando em subnets publicas sem NAT (egress ECR/Secrets/CloudWatch via IGW)
- Ingress das tasks vem apenas do SG do ALB (porta 8080); publico IP nao aceita inbound externo

### API Gateway (HTTP API v2)
- Protocolo HTTP API (v2) em vez do REST (v1): ~70% mais barato
- VPC Link v2 aponta diretamente ao ALB interno (nao ha NLB no path)
- Cognito JWT authorizer nativo nas rotas
- Rota `POST /access` -> Lambda access; `ANY /{proxy+}` -> ALB

### Cognito
- Dominio `assine-<env>-<6chars_account_id>` para evitar colisao global entre contas
- 2 clients: m2m (`client_credentials`) e web (`authorization_code`)

### Fora do escopo (sprints seguintes)
- Auto Scaling policies (requer Container Insights ou `AWS/ECS` CPU metric)
- WAF no ALB
- Canary/Blue-green (CodeDeploy)
- DynamoDB tables, S3 buckets (sprint futuro)
- Fiscal Lambda (Sprint 3)
- Stacks migration (vs. current classic root modules)

## Validacao

```bash
terraform fmt -recursive infra/terraform/
terraform -chdir=infra/terraform/envs/dev init -backend=false && terraform -chdir=infra/terraform/envs/dev validate
terraform -chdir=infra/terraform/envs/prod init -backend=false && terraform -chdir=infra/terraform/envs/prod validate
```

## Notes
- **Lambda db_bootstrap deps**: o pip install de `infra/terraform/modules/rds/files/requirements.txt` eh feito pelo CI (ver `.github/workflows/deploy.yml`). Em apply local, rode manualmente antes do terraform apply:
  ```bash
  pip install -q -r infra/terraform/modules/rds/files/requirements.txt -t infra/terraform/modules/rds/files --upgrade
  ```
- **Stripe secret**: `aws_secretsmanager_secret.stripe` eh criado com valor placeholder; atualize o valor no Console AWS apos o primeiro apply. O `lifecycle.ignore_changes = [secret_string]` garante que o apply nao sobrescreva.
- **FARGATE_SPOT**: tasks podem ser interrompidas com 2min de aviso. Aceitavel para cargas sem tolerancia dura a interrupcao (billing processa via SQS com idempotencia, content faz import async, subscriptions usa FIFO com redelivery). Para cargas criticas no futuro, desligue `use_fargate_spot` naquele servico.
- **GitHub Environment**: o workflow referencia `environment: prod`. Crie em *Settings > Environments > New environment > `prod`* e configure reviewers se quiser gate manual.
