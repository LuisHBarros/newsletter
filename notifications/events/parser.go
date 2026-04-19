package events

import (
	"encoding/json"
	"errors"
	"fmt"
	"go.uber.org/zap"
)

var (
	ErrMissingEventID      = errors.New("missing eventId")
	ErrMissingEventType    = errors.New("missing eventType")
	ErrInvalidSchemaVersion = errors.New("invalid schemaVersion")
)

var supportedTypes = map[string]bool{
	TypeSubscriptionActivated: true,
	TypeSubscriptionPastDue:   true,
	TypeSubscriptionCanceled:  true,
	TypeSubscriptionExpired:   true,
	TypeIssuePublished:        true,
	TypeIssueUpdated:          true,
}

func Parse(body []byte, logger *zap.Logger) (*Envelope, error) {
	var env Envelope
	if err := json.Unmarshal(body, &env); err != nil {
		return nil, fmt.Errorf("unmarshal envelope: %w", err)
	}

	if env.EventID == "" {
		return nil, ErrMissingEventID
	}
	if env.EventType == "" {
		return nil, ErrMissingEventType
	}
	if env.SchemaVersion != CurrentSchemaVersion {
		return nil, fmt.Errorf("%w: got %d, want %d", ErrInvalidSchemaVersion, env.SchemaVersion, CurrentSchemaVersion)
	}

	if !supportedTypes[env.EventType] {
		logger.Warn("unsupported event type, skipping", zap.String("eventType", env.EventType))
		return &env, nil
	}

	return &env, nil
}
