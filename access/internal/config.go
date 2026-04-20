package internal

import (
	"fmt"
	"os"
	"strings"
)

type Config struct {
	LogLevel          string
	PermissionsTable  string
	ContentServiceURL string
	DefaultTTLHours   int
}

func Load() *Config {
	return &Config{
		LogLevel:          getEnv("LOG_LEVEL", "info"),
		PermissionsTable:  getEnv("PERMISSIONS_TABLE", "content-permissions"),
		ContentServiceURL: getEnv("CONTENT_SERVICE_URL", ""),
		DefaultTTLHours:   getEnvInt("DEFAULT_TTL_HOURS", 168), // 7 days default
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return strings.TrimSpace(value)
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		var result int
		if _, err := fmt.Sscanf(value, "%d", &result); err == nil {
			return result
		}
	}
	return defaultValue
}
