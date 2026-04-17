# Architecture Overview

This is an AWS-based newsletter platform ("Assine") using event-driven microservices.

## System Architecture

**Client Layer**
- Browser / Mobile apps
- Authentication: Cognito (Magic link + OAuth2 + JWT)

**Edge Layer**
- API Gateway (routing + throttle)
- CloudFront + S3 (static assets CDN)
- Cognito for auth integration

**Compute Layer**

**ECS Fargate** — Java 21, Spring Boot, RDS, Outbox pattern
- `subscriptions` — Plans and subscriptions (database `newsletter_subscriptions`)
- `billing` — Stripe integration with Outbox pattern (database `newsletter_billing`)
- `content` — Notion webhook → HTML em S3, publica `content.issue.published` (database `newsletter_content`)
- One Fargate task per service; uma única instância RDS com database dedicado por serviço

**Lambda** — Golang (optimized for low cold-start)
- `access` — Content permissions
- `notifications` — Email via SES
- `fiscal` — NFS-e via Nuvem Fiscal

**Data Layer**
- RDS PostgreSQL — instância única `assine-db` com databases: `newsletter_subscriptions`, `newsletter_billing`, `newsletter_content` (cada serviço com usuário dedicado e acesso isolado ao seu database)
- **SQS** — Event bus + DLQ; `billing→subscriptions` usa FIFO com `MessageGroupId=subscriptionId` para ordenação e deduplicação; `content` consome `assine-content-jobs` (standard, import assíncrono) e publica em `assine-events`
- DynamoDB — Access permissions
- S3 — Frontend static files (newsletter signup), HTML de newsletters (renderizado pelo `content`), imagens, fiscal documents

**Services**
- SES — Transactional emails
- CloudWatch — Logs, metrics, alerts, traces

## Key Patterns

- **Outbox pattern** — Used in billing service for reliable event publishing
- **EventBridge Scheduler** — For recurring jobs (newsletter delivery, NFS-e generation)
- **SQS FIFO** — For billing→subscriptions events with `MessageGroupId=subscriptionId` and `MessageDeduplicationId=eventId` to guarantee ordering per subscription and prevent double-charge
- **X-Ray + CloudWatch Traces** — Essential for debugging latency across API GW → ECS → Lambda → SQS

## Infrastructure Notes

- Use **AWS Secrets Manager** for credentials (RDS, Stripe keys, Nuvem Fiscal tokens) with automatic rotation
- Configure **S3 Lifecycle Policies** to move old PDFs/images to Glacier Instant Retrieval after X days for cost optimization
- Integrate **aws-xray-sdk-go** in Lambda functions for explicit tracing

## Data Flow

Client → API Gateway → ECS (subscriptions/billing/content) or Lambda (access/notifications/fiscal)
Billing events → SQS FIFO (assine-subscriptions.fifo) → ECS subscriptions → Outbox → SQS (assine-events) → Lambda notifications → SES
Content service → S3 (PDFs, images)
All services → CloudWatch (logs, metrics, traces)
