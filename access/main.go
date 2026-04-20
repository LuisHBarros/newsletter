package main

import (
	"context"
	"fmt"
	"log"

	"github.com/assine/newsletter/access/internal"
	"github.com/assine/newsletter/access/permissions"
	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"go.uber.org/zap"
)

type app struct {
	cfg    *internal.Config
	logger *zap.Logger
}

func newApp() *app {
	cfg := internal.Load()
	logger := newLogger(cfg.LogLevel)

	return &app{
		cfg:    cfg,
		logger: logger,
	}
}

func newLogger(level string) *zap.Logger {
	cfg := zap.NewProductionConfig()
	switch level {
	case "debug":
		cfg.Level = zap.NewAtomicLevelAt(zap.DebugLevel)
	case "info":
		cfg.Level = zap.NewAtomicLevelAt(zap.InfoLevel)
	case "warn":
		cfg.Level = zap.NewAtomicLevelAt(zap.WarnLevel)
	case "error":
		cfg.Level = zap.NewAtomicLevelAt(zap.ErrorLevel)
	default:
		cfg.Level = zap.NewAtomicLevelAt(zap.InfoLevel)
	}
	logger, _ := cfg.Build()
	return logger
}

func (a *app) initHandler() (*Handler, error) {
	ctx := context.Background()

	awsCfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, fmt.Errorf("load aws config: %w", err)
	}

	dynamoClient := dynamodb.NewFromConfig(awsCfg)
	store := permissions.NewStore(dynamoClient, a.cfg.PermissionsTable)

	return NewHandler(store, a.logger), nil
}

func (a *app) apiGatewayHandler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	handler, err := a.initHandler()
	if err != nil {
		a.logger.Error("failed to initialize handler", zap.Error(err))
		log.Fatalf("fatal: handler init failed, forcing container recycle: %v", err)
	}

	return handler.HandleAPIGateway(ctx, request)
}

func main() {
	a := newApp()
	lambda.Start(a.apiGatewayHandler)
}
