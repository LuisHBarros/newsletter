# Ambientes: Produção (AWS) e Desenvolvimento (LocalStack/Docker)

Este documento descreve a estratégia de ambientes da plataforma Assine, justificando a escolha de **AWS em produção** e **LocalStack em Docker para desenvolvimento**, além de detalhar como cada perfil é ativado no serviço `subscriptions`.

## Visão Geral

| Aspecto | `dev` | `prod` (AWS) |
|---|---|---|
| Infra AWS | LocalStack (Docker) | AWS real (conta de prod) |
| Banco de dados | PostgreSQL local (Docker) | RDS PostgreSQL |
| Mensageria | SQS via LocalStack (`http://localhost:4566`) | SQS (FIFO p/ billing→subscriptions) |
| Segredos | LocalStack Secrets Manager | AWS Secrets Manager |
| Credenciais AWS | Estáticas/fake | IAM Instance Profile (ECS Task Role) |
| Auth (JWT) | Keycloak local (`localhost:9100`) | Cognito |
| Observabilidade | Logs locais, tracing 100% | CloudWatch + tracing 1% |
| Health details | `always` | `never` |

## Justificativa da Escolha

### Produção na AWS

- **Alinhamento com a arquitetura alvo** (ver `architecture.md`): ECS Fargate + RDS + SQS + Cognito + S3 + CloudWatch + X-Ray.
- **Escalabilidade gerenciada**: Fargate remove operação de nodes; RDS e SQS escalam sob demanda.
- **Segurança**: IAM Roles por task, Secrets Manager com rotação, KMS para cifragem em repouso, VPC isolada.
- **Resiliência**: Multi-AZ no RDS, DLQ no SQS, FIFO com `MessageGroupId=subscriptionId` e `MessageDeduplicationId=eventId` para eventos de billing (evita dupla cobrança no Stripe).
- **Observabilidade nativa**: CloudWatch Logs/Metrics e X-Ray para rastreio fim-a-fim (API Gateway → ECS → Lambda → SQS).
- **Custo previsível**: Lifecycle policies no S3 (Glacier IR) e sampling reduzido de traces (1%) em prod.

### Desenvolvimento com LocalStack em Docker

- **Paridade com produção**: LocalStack emula SQS, Secrets Manager, S3 e demais serviços usados, permitindo usar exatamente o mesmo código (Spring Cloud AWS) sem mocks.
- **Zero custo** e **offline**: desenvolvedor roda tudo localmente, sem depender de conta AWS nem consumir recursos pagos.
- **Isolamento por desenvolvedor**: cada dev tem seu próprio estado; testes não interferem entre times.
- **Feedback rápido**: subir stack em segundos via Docker, sem provisionamento de infra.
- **Onboarding simples**: apenas Docker + JDK 21 como pré-requisitos.
- **Testes de integração**: Testcontainers para Postgres garante schema real via Flyway; LocalStack cobre o lado AWS.

## Perfis Spring (`application.yml`)

O serviço `subscriptions` define dois perfis em `subscriptions/src/main/resources/application.yml`:

- **`dev`** (default): aponta para `localhost` (Postgres, Keycloak) e LocalStack (`http://localhost:4566`) para SQS e Secrets Manager. `instance-profile: false`, `show-sql: true`, tracing 100%.
- **`prod`: AWS real com variáveis de ambiente (`AWS_RDS_URL`, `AWS_REGION`, `COGNITO_ISSUER_URI`), `instance-profile: true` (ECS Task Role), CloudWatch metrics habilitado, sampling de tracing 1%, endpoints de actuator restritos (`health,info`), log level `WARN`, filas `*-prod`.

Ativação: `SPRING_PROFILES_ACTIVE=prod` (ECS Task Definition) ou padrão `dev` no desenvolvimento.

## Executando o Ambiente de Desenvolvimento

### Pré-requisitos

- Docker e Docker Compose
- Java 21
- Maven Wrapper (incluso)

### LocalStack via Docker

Exemplo de `docker-compose.yml` (sugestão — ainda não versionado):

```yaml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: subscriptions
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"

  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      SERVICES: sqs,secretsmanager,s3
      DEBUG: 0
      AWS_DEFAULT_REGION: us-east-1
    volumes:
      - "/var/run/docker.sock:/var/run/docker.sock"
```

Provisionamento das filas usadas pelo serviço:

```bash
# Standard queue for subscriptions published events
awslocal sqs create-queue --queue-name assine-events
awslocal sqs create-queue --queue-name assine-events-dlq

# FIFO queue for billing→subscriptions (ordering by subscriptionId)
awslocal sqs create-queue \
  --queue-name assine-subscriptions.fifo \
  --attributes '{
    "FifoQueue": "true",
    "ContentBasedDeduplication": "false",
    "DeduplicationScope": "messageGroup",
    "FifoThroughputLimit": "perMessageGroupId"
  }'
awslocal sqs create-queue \
  --queue-name assine-subscriptions-dlq.fifo \
  --attributes '{"FifoQueue": "true"}'
```

### Rodando o serviço

```bash
./mvnw spring-boot:run                 # usa perfil default 'local'
# ou
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

## Deploy em Produção (AWS)

### Secrets obrigatórios no AWS Secrets Manager

Antes do primeiro deploy, cadastre manualmente no AWS Console (ou via CLI) os seguintes secrets:

| Secret | Serviço | Formato | Onde obter |
|--------|---------|---------|------------|
| `assine/content/rds` | content | `{"username":"...","password":"...","host":"...","port":5432,"dbname":"..."}` | Criado automaticamente pelo Terraform RDS |
| `assine/content/notion-api-token` | content | `{"token":"secret_..."}` | [Notion Integrations](https://www.notion.so/my-integrations) |
| `assine/content/notion-webhook-secret` | content | `{"secret":"..."}` | String aleatória que você define |
| `assine/billing/rds` | billing | `{"username":"...","password":"...","host":"...","port":5432,"dbname":"..."}` | Criado automaticamente pelo Terraform RDS |
| `assine/billing/stripe` | billing | `{"api_key":"sk_live_...","webhook_secret":"whsec_..."}` | [Stripe Dashboard](https://dashboard.stripe.com/apikeys) |
| `assine/subscriptions/rds` | subscriptions | `{"username":"...","password":"...","host":"...","port":5432,"dbname":"..."}` | Criado automaticamente pelo Terraform RDS |

**Importante**: Após criar o secret, atualize o valor diretamente no Console AWS (clique no secret → "Retrieve secret value" → "Edit").

### SSM Parameter Store

| Parâmetro | Serviço | Descrição |
|-----------|---------|-----------|
| `/newsletter/subscriptions-api-key` | notifications | API key para autenticação m2m com o serviço `subscriptions` |

### Build e Deploy

- **Build**: `docker build -t assine/subscriptions:latest .` (ver `subscriptions/Dockerfile`).
- **Registry**: push para ECR.
- **Runtime**: ECS Fargate Task com `SPRING_PROFILES_ACTIVE=prod` e variáveis vindas do Secrets Manager / Parameter Store.
- **Rede**: atrás de API Gateway → ALB/Service Connect → ECS Service.
- **IAM**: Task Role com permissões mínimas para `sqs:*Message*` nas filas do serviço e `secretsmanager:GetSecretValue` dos segredos necessários.
- **Observabilidade**: sidecar/agent do CloudWatch Logs + X-Ray daemon (ou OTel Collector).
- **Alertas DLQ**: Alarme CloudWatch `sqs.dlq.messages > 0` por 5min dispara notificação SNS/SQS para o time (ver seção Observabilidade).

## Variáveis de Ambiente

### Obrigatórias em `dev`/`prod`

- `AWS_RDS_URL`, `AWS_RDS_USERNAME`, `AWS_RDS_PASSWORD`
- `COGNITO_ISSUER_URI`
- `AWS_REGION`
- `SPRING_PROFILES_ACTIVE=dev|prod`

### Opcionais em `dev`

- `SPRING_PROFILES_ACTIVE` (default `dev`)
- Endpoints de LocalStack já fixados em `application.yml`.

## Observabilidade e Alertas

### DLQ — Dead Letter Queue Monitoring

O serviço publica a métrica `sqs.dlq.messages` (Counter do Micrometer) com tag `queue=<nome>` sempre que uma mensagem chega à DLQ. Em produção:

**Alarme CloudWatch recomendado:**
```json
{
  "AlarmName": "Subscriptions-DLQ-Messages",
  "MetricName": "sqs.dlq.messages",
  "Namespace": "Assine/Subscriptions",
  "Dimensions": [{"Name": "queue", "Value": "assine-subscriptions-prod-dlq.fifo"}],
  "Statistic": "Sum",
  "Period": 300,
  "EvaluationPeriods": 1,
  "Threshold": 0,
  "ComparisonOperator": "GreaterThanThreshold",
  "TreatMissingData": "notBreaching"
}
```

**Ação**: SNS topic → email do time + possível PagerDuty.

**LocalStack/DEV**: Métrica visível em `/actuator/metrics/sqs.dlq.messages`.

## Decisões e Trade-offs

- **LocalStack Community** cobre os serviços usados pelo `subscriptions` (SQS, Secrets Manager, S3). Recursos pagos do LocalStack Pro não são necessários hoje.
- **Não usamos AWS real em dev por desenvolvedor** para evitar custo, risco de vazamento de credenciais e lentidão de feedback. O perfil `dev` existe para uma conta compartilhada de homologação/integração.
- **Cognito não é emulado pelo LocalStack Community** — por isso o perfil `dev` usa Keycloak (`http://localhost:9100/auth/realms/assine`) como Identity Provider compatível com OAuth2/JWT.
- **Testcontainers** é usado nos testes de integração em vez de LocalStack para o banco, por ser mais rápido e determinístico para schema/Flyway.
