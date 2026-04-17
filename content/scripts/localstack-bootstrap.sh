#!/usr/bin/env bash
# Bootstrap script for LocalStack: SQS queues (with DLQ + RedrivePolicy),
# S3 bucket for rendered HTML artifacts, Secrets Manager entries (RDS, Notion).

set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
MAX_RECEIVE_COUNT=5

aws_local() {
  aws --endpoint-url "$ENDPOINT" --region "$REGION" "$@"
}

echo "Creating DLQs..."
aws_local sqs create-queue --queue-name assine-events-dlq 2>/dev/null || true
aws_local sqs create-queue --queue-name assine-content-jobs-dlq 2>/dev/null || true

EVENTS_DLQ_ARN=$(aws_local sqs get-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-events-dlq --query 'QueueUrl' --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

JOBS_DLQ_ARN=$(aws_local sqs get-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-content-jobs-dlq --query 'QueueUrl' --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

echo "Creating source queues with RedrivePolicy..."
aws_local sqs create-queue --queue-name assine-events 2>/dev/null || true
aws_local sqs set-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-events --query 'QueueUrl' --output text)" \
  --attributes "{\"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${EVENTS_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"}"

aws_local sqs create-queue --queue-name assine-content-jobs 2>/dev/null || true
aws_local sqs set-queue-attributes \
  --queue-url "$(aws_local sqs get-queue-url --queue-name assine-content-jobs --query 'QueueUrl' --output text)" \
  --attributes "{\"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${JOBS_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"}"

echo "Creating S3 bucket assine-content-dev..."
aws_local s3api create-bucket --bucket assine-content-dev 2>/dev/null || true

echo "Verifying queues..."
for QUEUE in assine-events assine-events-dlq assine-content-jobs assine-content-jobs-dlq; do
  aws_local sqs get-queue-url --queue-name "$QUEUE" --query 'QueueUrl' --output text >/dev/null 2>&1 \
    && echo "  ✓ $QUEUE" || echo "  ✗ $QUEUE"
done

echo "Seeding Secrets Manager..."

put_secret() {
  local name="$1"
  local payload="$2"
  if aws_local secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
    aws_local secretsmanager put-secret-value --secret-id "$name" --secret-string "$payload" >/dev/null
    echo "  ↻ updated $name"
  else
    aws_local secretsmanager create-secret --name "$name" --secret-string "$payload" >/dev/null
    echo "  ✓ created $name"
  fi
}

RDS_SECRET_JSON='{"username":"content_user","password":"content_pwd","engine":"postgresql","host":"localhost","port":5432,"dbname":"newsletter_content"}'
NOTION_TOKEN_JSON='{"token":"secret_dev-change-me"}'
NOTION_WEBHOOK_JSON='{"secret":"dev-webhook-secret-change-me"}'

put_secret "assine/content/rds" "$RDS_SECRET_JSON"
put_secret "assine/content/notion-api-token" "$NOTION_TOKEN_JSON"
put_secret "assine/content/notion-webhook-secret" "$NOTION_WEBHOOK_JSON"

echo "Done. maxReceiveCount=${MAX_RECEIVE_COUNT}"
