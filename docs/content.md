# Serviço `content`

Serviço responsável por transformar páginas do Notion em artefatos HTML versionados em S3 e notificar o restante da plataforma via eventos.

## Papel na plataforma

- **Faz**: cataloga `Newsletter` + `Issue`, recebe webhook do Notion, renderiza HTML (pt-BR) em S3, emite eventos de ciclo de vida.
- **Não faz**: não envia email (delegado ao `notifications`), não resolve autorização por usuário (delegado ao `access`), não gera PDF na v1.

## Stack

- Spring Boot 3.5, Java 21, arquitetura hexagonal (mesmo padrão de `subscriptions`/`billing`).
- Postgres (`newsletter_content`), Flyway, JPA.
- Spring Cloud AWS 3.3 (SQS, S3, Secrets Manager).
- WebClient (Reactor) para Notion API + Resilience4j (retry/circuit breaker).
- ShedLock para locks distribuídos em `@Scheduled`.
- Outbox pattern para publicação confiável em `assine-events`.

## Modelo de dados

Database `newsletter_content` (ver `content/src/main/resources/db/migration/V1–V4__*.sql`):

- `newsletters` — agregado raiz (slug único, `notion_database_id`, status).
- `newsletter_plans` — `(newsletter_id, plan_id)` → origem da verdade para entitlement por plano.
- `issues` — `notion_page_id` único; slug único por newsletter; versão incremental.
- `issue_assets` — imagens re-hospedadas (evita hotlink Notion).
- `outbox_events`, `processed_events`, `shedlock` — padrões herdados.
- `notion_webhook_deliveries` — dedupe + auditoria de webhooks.

## Ingestão: webhook-first + reconciliação

```
Notion --webhook--> POST /webhooks/notion (HMAC)
                          │
                          ▼
       notion_webhook_deliveries (dedupe)
                          │
                          ▼
             SQS assine-content-jobs
                          │
                          ▼
             ContentJobsListener
                          │
                          ▼
             IssueImportService:
               fetchPage → upsert → HtmlRenderer → S3 → outbox
                          │
                          ▼
       outbox_events (PENDING)
                          │
           OutboxRelay (@Scheduled cron)
                          │
                          ▼
                SQS assine-events
                          │
       ┌──────────────────┴──────────────────┐
       ▼                                     ▼
   notifications (SES)                  access (signed URLs)
```

**Fallback**: `NotionReconciliationJob` roda a cada 15 min (cron em dev, EventBridge em prod) e re-enfileira páginas editadas após o `max(notion_last_edited_at)` do banco.

## State machine de `Issue`

```
                     import (published=false)
       import   ┌──> DRAFT
     ────────>  │
                ├──> SCHEDULED  ── scheduled_at ≤ now ──> PUBLISHED
                │    (import com published=true &
                │     scheduled_at futuro)
                │
                └──> PUBLISHED  (import com published=true & sem scheduled futuro)
                                                │
                                                ▼
                                           ARCHIVED (admin)
```

- `SCHEDULED → PUBLISHED` transita via `IssuePublishScheduler` (dev) ou `POST /api/v1/internal/jobs/publish-scheduled` chamado pelo EventBridge (prod).

## Slug das edições

`SlugService.resolve()`:
1. Tenta `slug` da property do Notion (se existe e não é vazia, normaliza com `slugify`).
2. Fallback: `slugify(title)`.
3. Em colisão dentro da newsletter: sufixo `-2`, `-3`, …

## Renderização HTML

`HtmlRenderer` converte blocos Notion em HTML semântico pt-BR. Suporta:
`paragraph`, `heading_1..3`, `bulleted_list_item`, `numbered_list_item`, `quote`, `callout`, `divider`, `code`, `image`, `bookmark`.

Saída fica em S3: `content/{newsletterSlug}/{issueId}/v{version}/index.html`.
Versões antigas viram Glacier IR após 180 dias (ver `application-prod.yml`).

## Eventos publicados

Fila: `assine-events` (standard). Envelope + schemas em `content/src/main/resources/contracts/*.schema.json`.

| Evento | Quando | Consumidores |
|---|---|---|
| `content.newsletter.created` | `POST /admin/newsletters` | `access` |
| `content.newsletter.plans_updated` | `PUT /admin/newsletters/{id}/plans` | `access` |
| `content.newsletter.archived` | `DELETE /admin/newsletters/{id}` | `access` |
| `content.issue.published` | primeira transição → `PUBLISHED` | `notifications`, `access` |
| `content.issue.updated` | re-render de issue já publicada | `notifications` (opcional) |

## Segurança

- **JWT (Cognito/Keycloak)** em `/api/v1/**`:
  - `content:read` — leitura pública autenticada.
  - `content:admin` — CRUD de catálogo + endpoints `/internal/jobs/*`.
- **Webhook Notion** em `/webhooks/notion`: HMAC-SHA256 validado em `HmacSignatureVerifier` contra o segredo `assine/content/notion-webhook-secret`; fora do filtro JWT.
- **Secrets Manager**: `assine/content/rds`, `assine/content/notion-api-token`, `assine/content/notion-webhook-secret`.
- Autorização fina ao HTML renderizado é feita pelo `access` via URLs pré-assinadas em S3 (nunca pelo `content`).

## Infra (prod)

- **ECS Fargate** task única; autoscale por CPU.
- **RDS** instância compartilhada com `newsletter_subscriptions`/`newsletter_billing`; user dedicado.
- **SQS**: `assine-content-jobs` + `assine-content-jobs-dlq` (standard); publica em `assine-events`.
- **EventBridge Scheduler** → API Gateway → ECS: `publish-scheduled` (cron(* * * * ? *)), `reconcile-notion` (rate(15 minutes)).
- **CloudWatch**: namespace `Assine/Content`, sampling X-Ray 1%, alarme `sqs.dlq.messages{queue=assine-content-jobs-dlq} > 0`.

## Dev / LocalStack

`docker compose up -d` (ver `content/docker-compose.yml`) sobe:
- Postgres com 3 databases (`newsletter_*`) e 3 usuários.
- LocalStack com SQS (`assine-events`, `assine-content-jobs`, + DLQs), S3 (`assine-content-dev`), Secrets Manager (segredos seeded).

`./mvnw spring-boot:run` inicia o serviço em perfil `local` (Keycloak em `localhost:9100` para JWT, LocalStack em `4566`, scheduler/relay habilitados).
