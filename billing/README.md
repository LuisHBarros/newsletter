# billing

Billing microservice for the Assine platform. Owns the payment-provider integration
(Stripe in production, a fake adapter locally) and keeps the billing-side view of
customers, subscriptions, and payments. Event-driven: reacts to `subscription.requested`
/ `subscription.cancel_requested` from the `subscriptions` service and publishes the
`billing.*` events that drive the subscription state machine.

## Layout

Mirrors the hexagonal layout from the `subscriptions` service:

```
src/main/java/com/assine/billing/
├── BillingApplication.java
├── config/                  # Spring security, SQS, scheduling, messaging wiring
├── domain/
│   ├── outbox/              # Generic outbox + processed_events (reused pattern)
│   ├── customer/            # BillingCustomer + BillingSubscription
│   └── payment/             # Payment + PaymentStatus + exceptions
├── application/
│   ├── outbox/              # Outbox processor, resilient publisher, EventRouter
│   ├── customer/            # Provision customers/subscriptions
│   └── payment/             # CreatePayment / GetPayment / StatusUpdate / Authorization
│       └── provider/        # PaymentProviderPort + FakePaymentProviderAdapter
└── adapters/
    ├── inbound/rest/        # PaymentController (read-only) + GlobalExceptionHandler
    └── outbound/
        ├── messaging/sqs/   # SqsEventPublisher + SqsEventConsumer + SqsDlqConsumer
        └── persistence/     # Outbox, payment, customer JPA adapters
```

## Domain mapping (from `payment-service` transfer → billing payment)

| transfer                          | billing                                               |
| --------------------------------- | ----------------------------------------------------- |
| `TransferEntity`                  | `Payment` (customer + optional subscription)          |
| `TransferStatus`                  | `PaymentStatus` (PENDING/SUCCEEDED/FAILED/REFUNDED/CANCELED) |
| `CreateTransferService`           | `CreatePaymentService`                                |
| `TransferAuthorizationService`    | `PaymentAuthorizationService`                         |
| `TransferStatusUpdateService`     | `PaymentStatusUpdateService`                          |
| `TransferStatusConsumer` (Kafka)  | `EventRouter` over SQS (`assine-billing`)             |
| `TransferController`              | `PaymentController` (read-only)                       |
| `TransferSpecification`           | `PaymentSpecification`                                |

## Event flow

Inbound queue: **`assine-billing`** (standard SQS, DLQ `assine-billing-dlq`).

```
subscription.requested  ── SQS ──▶ EventRouter ──▶ CreatePaymentService ─▶ Stripe PaymentIntent (PENDING)
                                                                                    │
                                                                  Stripe webhook ◀──┘
                                                                    │
                                       payment_intent.succeeded ──▶ StripeWebhookService
                                                                    │
                                                                    ├──▶ outbox: billing.payment.succeeded
                                                                    └──▶ outbox: billing.subscription.activated

                                       payment_intent.payment_failed ──▶ outbox: billing.payment.failed

subscription.cancel_requested ─ SQS ─▶ EventRouter ─▶ outbox: billing.subscription.canceled
```

`CreatePaymentService` only emits `billing.payment.failed` synchronously when the
provider rejects at creation (e.g. validation error). Success/failure of the actual
charge flows through the Stripe webhook endpoint, so in production the `Payment`
row stays `PENDING` until `payment_intent.succeeded` (or `.payment_failed`) arrives.

Outbound events go to `assine-events` with the standard envelope
(`eventId`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `occurredAt`, `payload`).

## Payment provider

Two adapters ship behind {@link PaymentProviderPort}, chosen by `billing.stripe.enabled`:

| property                   | adapter                           | when to use                  |
| -------------------------- | --------------------------------- | ---------------------------- |
| `billing.stripe.enabled=true`  | `StripePaymentProviderAdapter` | dev-with-real-keys, staging, prod |
| *unset* / `=false`         | `FakePaymentProviderAdapter`     | unit tests, local quick start |

Stripe secrets are loaded from Secrets Manager under `assine/billing/stripe`
(keys: `apiKey`, `webhookSecret`). Properties:

```yaml
billing:
  stripe:
    enabled: true
    api-key:        ${assine/billing/stripe:apiKey}
    webhook-secret: ${assine/billing/stripe:webhookSecret}
```

## Stripe webhook

**`POST /webhooks/stripe`** (bypass OAuth2; authenticated via HMAC `Stripe-Signature`).

- Signature verification uses `com.stripe.net.Webhook.constructEvent`.
- Duplicate event ids (`evt_...`) are deduped via the shared `processed_events` table
  (event id folded into a deterministic UUID).
- Supported event types: `payment_intent.succeeded`, `payment_intent.payment_failed`,
  `charge.refunded`. Unknown types return `200` with a log entry (so Stripe stops retrying).
- Responses:
  - `200 {"status":"ok"}` — processed.
  - `200 {"status":"duplicate"}` — already seen (idempotent).
  - `400 {"error":"missing Stripe-Signature header"}` / `"invalid signature"`.
  - `503 {"error":"stripe disabled"}` — `billing.stripe.enabled=false`.

## Running locally

Prereqs: Docker, Java 21.

```bash
# Start Postgres (DB=billing) + LocalStack (SQS + Secrets Manager) + bootstrap queues
docker compose up -d

# Run the app
./mvnw spring-boot:run
```

The bootstrap script provisions:

- `assine-events` + `assine-events-dlq` (published billing.* events)
- `assine-billing` + `assine-billing-dlq` (inbound subscription.* events)
- Secret `assine/billing/rds` with local Postgres credentials

## Tests

```bash
./mvnw test
```

Unit + slice tests (no Docker):

- `CreatePaymentServiceTest` — async contract: PENDING on happy path, `billing.payment.failed` on sync rejection.
- `PaymentAuthorizationServiceTest` — amount/currency/customer validation.
- `EventRouterTest` — all inbound event types (requested / trial / cancel / unknown / malformed).
- `StripeWebhookServiceTest` — succeeded / failed / refunded paths + idempotency + payment-not-found no-op.
- `StripeWebhookControllerTest` (`@WebMvcTest`) — signature failure, dispatch, dedup via `processed_events`.
- `PaymentControllerTest` (`@WebMvcTest`) — visibility rules, admin scope, filters, 404.
- `OutboxEventJpaRepositoryTest`, `PaymentJpaRepositoryTest`, `BillingCustomerJpaRepositoryTest`, `BillingSubscriptionJpaRepositoryTest` (`@DataJpaTest` on H2 w/ `MODE=PostgreSQL`).
- `PublishedEventsContractTest` — JSON Schemas under `resources/contracts/published/`.

Testcontainers tests (need Docker daemon API ≥ 1.40):

- `OutboxEventE2ETest` — Postgres + LocalStack SQS, full `OutboxEventService` lifecycle.
- `OutboxSkipLockedIntegrationTest` — asserts `FOR UPDATE SKIP LOCKED` disjoint claim & non-blocking.
- `ShedLockConcurrencyIntegrationTest` — JDBC-backed ShedLock grants one lock across racing threads.
- `BillingApplicationTests` — `@SpringBootTest` smoke.

```bash
./mvnw -q test                                       # everything (requires Docker)
./mvnw -q test -Dtest='!*IntegrationTest,!*E2ETest,!BillingApplicationTests'  # unit + slice only
```

## Out of scope (TODOs)

- NFS-e / fiscal events.
- Secrets Manager rotation.
- Dead-letter replay tooling for webhook processing failures (current path: 5xx from the service
  leaves the `processed_events` row in place after transaction rollback; duplicate handling is safe).
