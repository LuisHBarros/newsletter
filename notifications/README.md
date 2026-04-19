# Newsletter Notifications Service

AWS Lambda service that sends email notifications for subscription and content events.

## Architecture

- **Lambda**: Go runtime (provided.al2) processing SQS events
- **SQS**: Event ingestion queue with DLQ
- **DynamoDB**: Idempotency store with TTL
- **SES**: Email delivery via templates

## Events Handled

| Event Type | Template | Recipient |
|------------|----------|-----------|
| `subscription.activated` | `{env}-welcome-{version}` | User from payload |
| `subscription.past_due` | `{env}-payment_failed-{version}` | User from payload |
| `subscription.canceled` | `{env}-canceled-{version}` | User from payload |
| `subscription.expired` | `{env}-expired-{version}` | User from payload |
| `content.issue.published` | `{env}-newsletter_issue-{version}` | Active subscribers |
| `content.issue.updated` | `{env}-newsletter_errata-{version}` | Active subscribers |

## Fan-Out Behavior

Issue events fan out to all active subscribers:
- Fetches subscribers from subscriptions API with pagination
- Sends emails concurrently (max 10 parallel)
- Per-subscriber idempotency via `eventID:userID`

## Configuration

Environment variables:
- `LOG_LEVEL` - debug|info|warn|error
- `SENDER_EMAIL` - SES verified sender
- `SUBSCRIPTIONS_API_URL` - Subscriptions service endpoint
- `TEMPLATE_PREFIX` - Template name prefix (e.g., `prod-`)
- `PROCESSED_EVENTS_TABLE` - DynamoDB idempotency table
- `SUBSCRIPTIONS_API_KEY` - API key for subscriptions service (from SSM)

## Deployment

```bash
# Build
GOOS=linux GOARCH=amd64 go build -o bootstrap -tags lambda.norpc

# Deploy
sam build
sam deploy --guided
```

## Testing

```bash
go test ./...
```

## Idempotency

Events are marked processed **after** email send (at-least-once delivery). DynamoDB item expires after TTL (7 days) for automatic cleanup.
