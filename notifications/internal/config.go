package internal

import (
	"os"
	"strconv"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

type Config struct {
	ProcessedEventsTable   string
	SenderEmail            string
	TemplatePrefix         string
	LogLevel               zapcore.Level
	SubscriptionsAPIURL    string
	SubscriptionsAPIKey    string
	SubscriptionsAPIKeySSM string
	MaxSubscribersPerEvent int
}

func Load() *Config {
	cfg := &Config{
		ProcessedEventsTable:   getEnv("PROCESSED_EVENTS_TABLE", "processed_events"),
		SenderEmail:            getEnv("SENDER_EMAIL", "noreply@assine.news"),
		TemplatePrefix:         getEnv("TEMPLATE_PREFIX", "assine"),
		LogLevel:               zapcore.InfoLevel,
		SubscriptionsAPIURL:    getEnv("SUBSCRIPTIONS_API_URL", ""),
		SubscriptionsAPIKey:    getEnv("SUBSCRIPTIONS_API_KEY", ""),
		SubscriptionsAPIKeySSM: getEnv("SUBSCRIPTIONS_API_KEY_SSM", ""),
		MaxSubscribersPerEvent: getEnvInt("MAX_SUBSCRIBERS_PER_EVENT", 5000),
	}

	if lvl := os.Getenv("LOG_LEVEL"); lvl != "" {
		if parsed, err := zapcore.ParseLevel(lvl); err == nil {
			cfg.LogLevel = parsed
		}
	}

	return cfg
}

func NewLogger(level zapcore.Level) *zap.Logger {
	cfg := zap.NewProductionConfig()
	cfg.Level = zap.NewAtomicLevelAt(level)
	cfg.EncoderConfig.TimeKey = "ts"
	cfg.EncoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder
	logger, _ := cfg.Build()
	return logger
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}
