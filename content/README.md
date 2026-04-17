# content

Serviço Spring Boot 3.5 / Java 21 que sincroniza newsletters do **Notion** para **S3** (HTML), publica eventos no SQS `assine-events` e é consumido pelo `notifications` (envio de email) e `access` (autorização).

Decisões-chave (ver `docs/content.md` e `/home/luish/.windsurf/plans/content-architecture-6088d5.md`):

- **Híbrido**: metadados em Postgres (database `newsletter_content`), corpo no Notion, HTML renderizado em S3.
- **HTML only na v1** — sem PDF.
- **Multi-newsletter por plano** (`newsletter_plans` → `access` valida entitlement).
- **Webhook Notion** (HMAC-SHA256) + **reconciliação** a cada 15 min como fallback.
- **Outbox pattern** para publicação confiável em SQS.

## Rodando localmente

```bash
# 1. sobe Postgres (3 databases) + LocalStack (SQS, S3, Secrets Manager)
docker compose up -d
# bootstrap script executa automático: cria filas, bucket S3 e segredos

# 2. inicia o serviço (perfil default: local)
./mvnw spring-boot:run
```

Endpoints principais:

- `POST /webhooks/notion` — webhook Notion (HMAC obrigatório; sem JWT).
- `POST /api/v1/admin/newsletters` — cria newsletter (scope `content:admin`).
- `PUT  /api/v1/admin/newsletters/{id}/plans` — atualiza mapping plan ⇄ newsletter.
- `POST /api/v1/admin/issues/import` — força import/re-render de uma página Notion.
- `GET  /api/v1/newsletters` — lista newsletters (scope `content:read`).
- `GET  /api/v1/newsletters/{slug}/issues` — lista edições publicadas.
- `POST /api/v1/internal/jobs/publish-scheduled` — target do EventBridge Scheduler em prod.
- `POST /api/v1/internal/jobs/reconcile-notion` — target do EventBridge Scheduler em prod.

## Eventos publicados (via outbox → SQS `assine-events`)

| Evento | Consumidor primário |
|---|---|
| `content.newsletter.created` | `access` |
| `content.newsletter.plans_updated` | `access` |
| `content.newsletter.archived` | `access` |
| `content.issue.published` | `notifications`, `access` |
| `content.issue.updated` | `notifications` (errata opcional) |

Schemas formais: `src/main/resources/contracts/*.schema.json`.

## Estrutura

```
com.assine.content
├── adapters
│   ├── inbound   (REST: webhook, admin, public, internal | SQS listener)
│   └── outbound  (JPA, SQS publishers, S3, Notion WebClient, HTML renderer)
├── application   (use cases orquestrando domínio)
├── domain        (modelos, repositórios, ports)
└── config        (Security, SQS, Notion, Scheduling, ApplicationConfig)
```

## Testes

```bash
./mvnw test
```

O smoke test `ContentApplicationTests#contextLoads` valida o wiring completo do contexto Spring em perfil `test` (H2 + credenciais fake AWS).
