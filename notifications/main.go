package main

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"github.com/assine/newsletter/notifications/email"
	"github.com/assine/newsletter/notifications/idempotency"
	"github.com/assine/newsletter/notifications/internal"
	"github.com/assine/newsletter/notifications/subscriptions"
	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/sesv2"
	"go.uber.org/zap"
)

type app struct {
	cfg         *internal.Config
	logger      *zap.Logger
	handler     *Handler
	handlerOnce sync.Once
	handlerErr  error
}

func newApp() *app {
	cfg := internal.Load()
	logger := internal.NewLogger(cfg.LogLevel)

	return &app{
		cfg:    cfg,
		logger: logger,
	}
}

func (a *app) initHandler() error {
	a.handlerOnce.Do(func() {
		ctx := context.Background()

		awsCfg, err := config.LoadDefaultConfig(ctx)
		if err != nil {
			a.handlerErr = fmt.Errorf("load aws config: %w", err)
			return
		}

		sesClient := sesv2.NewFromConfig(awsCfg)
		dynamoClient := dynamodb.NewFromConfig(awsCfg)

		emailClient := email.NewClient(sesClient, a.cfg.SenderEmail)
		idempotencyStore := idempotency.NewStore(dynamoClient, a.cfg.ProcessedEventsTable)
		subscriptionsClient, err := subscriptions.NewClient(a.cfg.SubscriptionsAPIURL, a.cfg.SubscriptionsAPIKey)
		if err != nil {
			a.handlerErr = fmt.Errorf("init subscriptions client: %w", err)
			return
		}

		a.handler = NewHandler(emailClient, idempotencyStore, subscriptionsClient, a.cfg.TemplatePrefix, a.logger)
	})
	return a.handlerErr
}

func (a *app) sqsHandler(ctx context.Context, sqsEvent events.SQSEvent) (events.SQSEventResponse, error) {
	if err := a.initHandler(); err != nil {
		a.logger.Error("failed to initialize handler", zap.Error(err))
		return events.SQSEventResponse{}, err
	}

	var batchFailures []events.SQSBatchItemFailure

	for _, record := range sqsEvent.Records {
		if err := a.handler.Handle(ctx, []byte(record.Body)); err != nil {
			if errors.Is(err, ErrParseFail) {
				a.logger.Warn("parse failure, sending to DLQ",
					zap.String("messageId", record.MessageId),
					zap.Error(err),
				)
				batchFailures = append(batchFailures, events.SQSBatchItemFailure{
					ItemIdentifier: record.MessageId,
				})
				continue
			}

			a.logger.Error("processing failure",
				zap.String("messageId", record.MessageId),
				zap.Error(err),
			)
			batchFailures = append(batchFailures, events.SQSBatchItemFailure{
				ItemIdentifier: record.MessageId,
			})
			continue
		}
	}

	return events.SQSEventResponse{BatchItemFailures: batchFailures}, nil
}

func main() {
	a := newApp()
	lambda.Start(a.sqsHandler)
}
