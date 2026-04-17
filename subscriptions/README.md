# Subscriptions Service

Part of the Assine newsletter platform. Manages plans and subscriptions using the Outbox pattern for reliable event publishing.

## Architecture

- **Java 21** with Spring Boot 3.5
- **Ports and Adapters (Hexagonal Architecture)**
  - `domain/` - Core business logic, entities, repository interfaces, ports
  - `application/` - Application services and use cases
  - `infrastructure/` - External concerns (persistence, messaging)
  - `adapters/` - Inbound (REST controllers) and outbound implementations
  - `shared/` - Shared utilities

## Tech Stack

- **Framework**: Spring Boot 3.5, Spring Security, Spring Data JPA
- **Database**: PostgreSQL with Flyway migrations
- **Messaging**: SQS (Outbox pattern)
- **Security**: OAuth2 Resource Server (Cognito JWT validation)
- **Resilience**: Resilience4j Circuit Breaker
- **Observability**: Micrometer Tracing (OTel), X-Ray, CloudWatch Metrics
- **AWS**: Spring Cloud AWS (SQS, Secrets Manager)
- **Productivity**: Lombok, MapStruct
- **Testing**: Testcontainers (PostgreSQL)

## Profiles

- `local` - Local development with LocalStack for AWS services
- `dev` - Development environment with AWS services
- `prod` - Production environment

## Running Locally

### Prerequisites
- Java 21
- Docker & Docker Compose
- (Optional) LocalStack CLI for standalone usage

### Quick Start with Docker Compose

The simplest way to start all dependencies (PostgreSQL + LocalStack + bootstrap):

```bash
docker compose up -d postgres localstack bootstrap
```

This starts:
- **PostgreSQL** on port 5432 (database: `subscriptions`, user: `postgres`, password: `postgres`)
- **LocalStack** on port 4566 (SQS, Secrets Manager)
- **Bootstrap** job that creates required queues and secrets

Then run the application:

```bash
./mvnw spring-boot:run
```

### Manual Setup (without Docker)

If you prefer running dependencies manually:

```bash
# Start PostgreSQL
createdb subscriptions

# Start LocalStack
localstack start

# Bootstrap queues
./scripts/localstack-bootstrap.sh

# Run application
./mvnw spring-boot:run
```

## Building

```bash
./mvnw clean package
```

## Docker Build

```bash
docker build -t assine/subscriptions:latest .
```

## Database Schema

See `src/main/resources/db/migration/V1__init.sql` for initial schema.

## Outbox Pattern

The service uses the Outbox pattern for reliable event publishing:
- Events are written to `outbox_events` table within the same transaction as business changes
- A background process polls for pending events and publishes to SQS
- Once published, events are marked as `PUBLISHED`

## Environment Variables

### Required (dev/prod)
- `AWS_RDS_URL` - PostgreSQL connection URL
- `AWS_RDS_USERNAME` - Database username
- `AWS_RDS_PASSWORD` - Database password
- `COGNITO_ISSUER_URI` - Cognito issuer URI for JWT validation
- `AWS_REGION` - AWS region

### Optional
- `SPRING_PROFILES_ACTIVE` - Active profile (default: local)

## SQS DLQ & Retry Policy

The service uses **SQS-driven retry** (not in-process Spring Retry). When a message fails processing in `SqsEventConsumer`, it is **not acknowledged** (`AcknowledgementMode.ON_SUCCESS`) and returns to the queue after the visibility timeout expires. After `maxReceiveCount` failed attempts, SQS moves the message to the configured **Dead-Letter Queue (DLQ)**.

### Flow

1. Message consumed from source queue (e.g. `assine-subscriptions`)
2. On success → message acknowledged and deleted
3. On failure → exception re-thrown, message becomes visible again after `messageVisibility`
4. After `maxReceiveCount` (default 5) failures → SQS RedrivePolicy moves message to DLQ
5. `SqsDlqConsumer` logs the DLQ message and increments `sqs.dlq.messages` counter (tagged by queue)

### Tunable Parameters (`aws.sqs.listener`)

| Parameter | Default | Description |
|---|---|---|
| `max-messages-per-poll` | 10 | Max messages fetched per poll cycle |
| `poll-timeout` | 10s | Wait time for long-polling |
| `message-visibility` | 30s (prod: 60s) | Visibility timeout — should exceed max processing time |
| `max-receive-count` | 5 | Failed attempts before DLQ (applied by RedrivePolicy on the queue) |

### LocalStack Setup

```bash
# Option 1: docker-compose (includes bootstrap)
docker compose up

# Option 2: manual bootstrap with running LocalStack
./scripts/localstack-bootstrap.sh
```

The bootstrap script creates:
- DLQs: `assine-events-dlq`, `assine-subscriptions-dlq`
- Source queues: `assine-events`, `assine-subscriptions` with `RedrivePolicy` pointing to their DLQs

### Dev/Prod Setup

Create the same queue structure via AWS Console, CloudFormation, or Terraform:
- Create DLQ first, note its ARN
- Create source queue with `RedrivePolicy` = `{ "deadLetterTargetArn": "<DLQ-ARN>", "maxReceiveCount": 5 }`

### Observability

- **DLQ metric**: `sqs.dlq.messages` (CloudWatch / Micrometer) tagged with `queue`
- **Logs**: DLQ messages logged at ERROR level with `messageId`, `eventType`, `approximateReceiveCount`, `sentTimestamp`

## Health Checks

- `/actuator/health` - Application health
- `/actuator/metrics` - Metrics (dev)
- `/actuator/prometheus` - Prometheus metrics (dev)
