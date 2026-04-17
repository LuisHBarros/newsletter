#!/usr/bin/env bash
# Bootstrap script for LocalStack SQS queues with DLQ + RedrivePolicy
# Usage: ./scripts/localstack-bootstrap.sh
# Requires: awscli-local (pip install awscli-local) or aws --endpoint-url

set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
MAX_RECEIVE_COUNT=5

aws_local() {
  aws --endpoint-url "$ENDPOINT" --region "$REGION" "$@"
}

echo "Creating DLQs..."

# Standard DLQ for events (subscriptions published events)
aws_local sqs create-queue --queue-name assine-events-dlq 2>/dev/null || true

# FIFO DLQ for subscriptions (billing -> subscriptions) to ensure ordering
aws_local sqs create-queue \
  --queue-name assine-subscriptions-dlq.fifo \
  --attributes '{
    "FifoQueue": "true",
    "ContentBasedDeduplication": "false",
    "DeduplicationScope": "messageGroup",
    "FifoThroughputLimit": "perMessageGroupId"
  }' 2>/dev/null || true

EVENTS_DLQ_ARN=$(aws_local sqs get-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-events-dlq --query 'QueueUrl' --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

SUBSCRIPTIONS_DLQ_ARN=$(aws_local sqs get-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-subscriptions-dlq.fifo --query 'QueueUrl' --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

echo "Creating source queues with RedrivePolicy..."

# Standard queue for events published by subscriptions
aws_local sqs create-queue --queue-name assine-events 2>/dev/null || true
aws_local sqs set-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-events --query 'QueueUrl' --output text)" \
  --attributes "{\"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${EVENTS_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"}"

# FIFO queue for billing -> subscriptions (guarantees ordering per subscriptionId)
# MessageGroupId must be subscriptionId (provided by billing producer)
# MessageDeduplicationId must be eventId (provided by billing producer)
aws_local sqs create-queue \
  --queue-name assine-subscriptions.fifo \
  --attributes "{
    \"FifoQueue\": \"true\",
    \"ContentBasedDeduplication\": \"false\",
    \"DeduplicationScope\": \"messageGroup\",
    \"FifoThroughputLimit\": \"perMessageGroupId\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${SUBSCRIPTIONS_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"
  }" 2>/dev/null || true

# Standard DLQ for billing inbound queue (subscriptions -> billing)
aws_local sqs create-queue --queue-name assine-billing-dlq 2>/dev/null || true

BILLING_DLQ_ARN=$(aws_local sqs get-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-billing-dlq --query 'QueueUrl' --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

# Standard queue for events routed from subscriptions to billing
aws_local sqs create-queue --queue-name assine-billing 2>/dev/null || true
aws_local sqs set-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-billing --query 'QueueUrl' --output text)" \
  --attributes "{\"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${BILLING_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"}"

echo "Verifying queues..."

for QUEUE in assine-events assine-events-dlq assine-subscriptions.fifo assine-subscriptions-dlq.fifo assine-billing assine-billing-dlq; do
  URL=$(aws_local sqs get-queue-url --queue-name "$QUEUE" --query 'QueueUrl' --output text 2>/dev/null && echo "  ✓ $QUEUE" || echo "  ✗ $QUEUE (not found)")
done

echo "Seeding Secrets Manager..."

put_secret() {
  local name="$1"
  local payload="$2"
  if aws_local secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
    aws_local secretsmanager put-secret-value \
      --secret-id "$name" \
      --secret-string "$payload" >/dev/null
    echo "  ↻ updated $name"
  else
    aws_local secretsmanager create-secret \
      --name "$name" \
      --secret-string "$payload" >/dev/null
    echo "  ✓ created $name"
  fi
}

RDS_SECRET_JSON='{"username":"postgres","password":"postgres","engine":"postgresql","host":"localhost","port":5432,"dbname":"subscriptions_db"}'

put_secret "assine/subscriptions/rds" "$RDS_SECRET_JSON"

echo "Done. maxReceiveCount=${MAX_RECEIVE_COUNT}"
