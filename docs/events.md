# Event Contracts

Contratos dos eventos trocados entre microserviços do Assine via SQS. Cada microserviço mantém seus schemas em `<service>/src/main/resources/contracts/` (`subscriptions`, `billing`, `content`). Todos seguem JSON Schema Draft 2020-12 e são a fonte da verdade para validação.

## Envelope

Todo evento publicado via outbox segue o envelope:

```json
{
  "eventId": "7b9a3c...",           // UUID, chave de idempotência
  "schemaVersion": 1,
  "occurredAt": "2026-04-16T19:45:00Z",
  "eventType": "subscription.requested",
  "aggregateType": "Subscription",
  "aggregateId": "c5f8...",         // UUID do agregado
  "payload": { ... }                 // específico do evento
}
```

- **eventId** — gerado pelo publisher; consumidores devem deduplicar por ele (SQS é at-least-once).
- **schemaVersion** — incrementa só em mudança breaking; evoluções compatíveis ficam na mesma versão.
- **occurredAt** — ISO-8601 UTC.

## Eventos publicados por `subscriptions`

Consumidos por `billing` (intents) e por `access` / `notifications` (state changes). Eventos `subscription.*` e `plan.*` são roteados pelo publisher para a fila inbound do billing; demais eventos vão para `assine-events` (fila padrão).

| Evento | Tipo | Consumidor primário | Descrição |
|---|---|---|---|
| `subscription.requested` | intent | `billing` | Usuário pediu assinatura; billing deve criar Customer/Subscription no PSP. |
| `subscription.cancel_requested` | intent | `billing` | Usuário pediu cancelamento; billing cancela no PSP. |
| `subscription.activated` | state | `access`, `notifications` | Assinatura passou para `ACTIVE` após confirmação do billing. |
| `subscription.past_due` | state | `notifications` | Pagamento falhou; grace period iniciado. |
| `subscription.canceled` | state | `access`, `notifications` | Assinatura confirmada como cancelada. |
| `subscription.updated` | state | `access` | Alteração administrativa. |
| `subscription.expired` | state | `access`, `notifications` | Período corrente terminou sem renovação (FSM terminal). |
| `plan.created` / `plan.updated` / `plan.deleted` | state | `billing` (sync de preços) | Ciclo de vida do catálogo. |

### Exemplo — `subscription.past_due`

```json
{
  "payload": {
    "subscriptionId": "c5f8...",
    "userId": "8a1b..."
  }
}
```

### Exemplo — `subscription.requested`

```json
{
  "eventId": "7b9a3c6e-1d2f-4a5b-9c8d-0e1f2a3b4c5d",
  "eventType": "subscription.requested",
  "schemaVersion": 1,
  "aggregateType": "Subscription",
  "aggregateId": "c5f8e4a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
  "occurredAt": "2026-04-16T19:45:00Z",
  "payload": {
    "subscriptionId": "c5f8e4a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
    "userId": "8a1b2c3d-4e5f-6789-0abc-def012345678",
    "planId": "11111111-2222-3333-4444-555555555555",
    "status": "PENDING_PAYMENT",
    "planSnapshot": {
      "price": 29.90,
      "currency": "BRL",
      "billingInterval": "MONTHLY",
      "trialDays": 0
    },
    "metadata": {}
  }
}
```

### Exemplo — `subscription.activated`

```json
{
  "payload": {
    "subscriptionId": "c5f8...",
    "userId": "8a1b...",
    "planId": "1111...",
    "currentPeriodStart": "2026-04-16T19:45:00Z",
    "currentPeriodEnd":   "2026-05-16T19:45:00Z"
  }
}
```

### Exemplo — `subscription.period_renewed`

```json
{
  "eventId": "8c4d5e6f-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
  "eventType": "subscription.period_renewed",
  "schemaVersion": 1,
  "aggregateType": "Subscription",
  "aggregateId": "c5f8e4a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
  "occurredAt": "2026-05-16T19:45:00Z",
  "payload": {
    "subscriptionId": "c5f8e4a1-2b3c-4d5e-6f7a-8b9c0d1e2f3a",
    "userId": "8a1b2c3d-4e5f-6789-0abc-def012345678",
    "planId": "11111111-2222-3333-4444-555555555555",
    "currentPeriodStart": "2026-05-16T19:45:00Z",
    "currentPeriodEnd": "2026-06-16T19:45:00Z",
    "renewalType": "payment_succeeded"
  }
}
```

## Eventos consumidos por `subscriptions`

Publicados por `billing` e roteados em `EventRouter` para transições no domínio.

| Evento | Transição no domínio |
|---|---|
| `billing.subscription.activated` | `PENDING_PAYMENT`/`TRIAL` → `ACTIVE` (seta período corrente) |
| `billing.payment.succeeded` | renova `currentPeriodStart/End` |
| `billing.payment.failed` | → `PAST_DUE` |
| `billing.subscription.canceled` | → `CANCELED` (seta `canceledAt`) |

### Exemplo — `billing.subscription.activated`

```json
{
  "payload": {
    "subscriptionId": "c5f8...",
    "currentPeriodStart": "2026-04-16T19:45:00Z",
    "currentPeriodEnd":   "2026-05-16T19:45:00Z",
    "billingRef": "sub_1P...abc"
  }
}
```

### Exemplo — `billing.payment.failed`

```json
{
  "payload": {
    "subscriptionId": "c5f8...",
    "attempts": 3,
    "nextRetryAt": "2026-04-18T19:45:00Z",
    "reason": "card_declined"
  }
}
```

## Máquina de estados

```
                subscription.requested
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
       PENDING_PAYMENT               TRIAL
            │                         │
            │  billing.subscription.activated
            ▼                         ▼
          ┌───────── ACTIVE ─────────┐
          │            ▲              │
          │  billing.payment.succeeded (renewPeriod)
          │      ↓                    │
          │  subscription.period_renewed
          │  billing.payment.failed   │
          ▼                           │
       PAST_DUE ──────────────────────┘
          │
     billing.subscription.canceled
          │
          ▼
       CANCELED
```

`EXPIRED` é terminal: `ACTIVE` / `PAST_DUE` / `TRIAL` → `EXPIRED` quando `currentPeriodEnd` passa sem renovação.

O job de expiração é disparado por:
- **dev**: `ExpirationScheduler` (`@Scheduled(cron = "0 0 * * * *")`), habilitado por `subscriptions.expiration.scheduler.enabled=true`.
- **prod**: EventBridge Scheduler chama `POST /internal/jobs/expire-subscriptions` (scope `subscriptions:admin`); `@Scheduled` fica desabilitado via `subscriptions.expiration.scheduler.enabled=false`.

## Idempotência e ordenação

- Deduplicação obrigatória por `eventId` no consumer implementada via tabela `processed_events`.
- A fila `billing → subscriptions` (**assine-subscriptions.fifo**) usa **SQS FIFO** com `MessageGroupId = subscriptionId` para garantir ordem por agregado. O producer (billing) deve fornecer `MessageGroupId=subscriptionId` e `MessageDeduplicationId=eventId`.
- Eventos publicados por `subscriptions` (`assine-events`) usam fila padrão — consumidores devem tolerar reordenação.

## Eventos publicados por `content`

Consumidos por `notifications` (envio de email) e `access` (sincronização de entitlement e URLs pré-assinadas).

| Evento | Tipo | Consumidor primário | Descrição |
|---|---|---|---|
| `content.newsletter.created` | state | `access` | Nova newsletter criada; payload inclui `planIds[]`. |
| `content.newsletter.plans_updated` | state | `access` | Mapping newsletter ⇄ planos atualizado. |
| `content.newsletter.archived` | state | `access` | Newsletter arquivada (soft-delete). |
| `content.issue.published` | state | `notifications`, `access` | Edição publicada; payload carrega `htmlS3Key` e `planIds[]` para fan-out. |
| `content.issue.updated` | state | `notifications` (opcional) | Re-render de issue já publicada (errata); payload com paridade a `content.issue.published`: `newsletterSlug`, `title`, `slug`, `htmlS3Key`, `publishedAt`, `planIds[]`, `version`. |

### Exemplo — `content.issue.published`

```json
{
  "eventId": "9d1e7f32-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
  "eventType": "content.issue.published",
  "schemaVersion": 1,
  "aggregateType": "Issue",
  "aggregateId": "a1b2c3d4-e5f6-7890-abcd-ef0123456789",
  "occurredAt": "2026-04-17T13:15:00Z",
  "payload": {
    "newsletterId": "11111111-2222-3333-4444-555555555555",
    "newsletterSlug": "tech",
    "issueId": "a1b2c3d4-e5f6-7890-abcd-ef0123456789",
    "title": "Edição 42: O retorno das queues FIFO",
    "slug": "edicao-42-o-retorno-das-queues-fifo",
    "htmlS3Key": "content/tech/a1b2c3d4-e5f6-7890-abcd-ef0123456789/v1/index.html",
    "publishedAt": "2026-04-17T13:15:00Z",
    "planIds": ["11111111-2222-3333-4444-555555555555"]
  }
}
```

### Exemplo — `content.issue.updated`

```json
{
  "eventId": "be2f8c41-6a7c-5d8e-9f0a-1b2c3d4e5f6a",
  "eventType": "content.issue.updated",
  "schemaVersion": 1,
  "aggregateType": "Issue",
  "aggregateId": "a1b2c3d4-e5f6-7890-abcd-ef0123456789",
  "occurredAt": "2026-04-17T14:30:00Z",
  "payload": {
    "newsletterId": "11111111-2222-3333-4444-555555555555",
    "newsletterSlug": "tech",
    "issueId": "a1b2c3d4-e5f6-7890-abcd-ef0123456789",
    "version": 2,
    "title": "Edição 42: O retorno das queues FIFO",
    "slug": "edicao-42-o-retorno-das-queues-fifo",
    "htmlS3Key": "content/tech/a1b2c3d4-e5f6-7890-abcd-ef0123456789/v2/index.html",
    "publishedAt": "2026-04-17T13:15:00Z",
    "planIds": ["11111111-2222-3333-4444-555555555555"]
  }
}
```

Schemas formais em `content/src/main/resources/contracts/*.schema.json`.

## Versionamento

- Mudanças backward-compatible (campos opcionais novos) mantêm `schemaVersion`.
- Mudanças breaking criam novo `eventType.vN` (ex.: `subscription.requested.v2`) coexistindo com a v1 durante a migração.
