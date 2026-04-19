package events

import (
	"testing"
	"time"

	"errors"

	"go.uber.org/zap"
)

func TestParse(t *testing.T) {
	logger := zap.NewNop()

	tests := []struct {
		name    string
		body    string
		wantErr bool
		errType error
	}{
		{
			name:    "valid subscription activated event",
			body:    `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated","aggregateType":"subscription","aggregateId":"sub-456","payload":{"subscriptionId":"sub-456","userId":"usr-789","userEmail":"test@example.com","planId":"plan-basic"}}`,
			wantErr: false,
		},
		{
			name:    "valid issue published event",
			body:    `{"eventId":"evt-789","schemaVersion":1,"occurredAt":"2024-01-15T12:00:00Z","eventType":"content.issue.published","aggregateType":"issue","aggregateId":"iss-123","payload":{"newsletterId":"nl-456","newsletterSlug":"tech-weekly","issueId":"iss-123","title":"Weekly Tech Roundup","slug":"weekly-tech-roundup","htmlS3Key":"issues/iss-123.html","publishedAt":"2024-01-15T12:00:00Z","planIds":["plan-basic","plan-premium"]}}`,
			wantErr: false,
		},
		{
			name:    "missing eventId",
			body:    `{"schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated"}`,
			wantErr: true,
			errType: ErrMissingEventID,
		},
		{
			name:    "missing eventType",
			body:    `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z"}`,
			wantErr: true,
			errType: ErrMissingEventType,
		},
		{
			name:    "invalid schema version",
			body:    `{"eventId":"evt-123","schemaVersion":2,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated"}`,
			wantErr: true,
			errType: ErrInvalidSchemaVersion,
		},
		{
			name:    "unsupported event type returns envelope without error",
			body:    `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"unknown.event","aggregateType":"test","aggregateId":"test-123"}`,
			wantErr: false,
		},
		{
			name:    "invalid json",
			body:    `{"invalid json`,
			wantErr: true,
		},
		{
			name:    "empty payload - handled by caller",
			body:    `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated","aggregateType":"subscription","aggregateId":"sub-456","payload":{}}`,
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			env, err := Parse([]byte(tt.body), logger)
			if (err != nil) != tt.wantErr {
				t.Errorf("Parse() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if tt.errType != nil && !errors.Is(err, tt.errType) {
				t.Errorf("Parse() error = %v, want %v", err, tt.errType)
			}
			if err == nil && env == nil {
				t.Error("Parse() returned nil envelope without error")
			}
		})
	}
}

func TestParsePayloadExtraction(t *testing.T) {
	logger := zap.NewNop()

	t.Run("subscription payload extracted correctly", func(t *testing.T) {
		body := `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated","aggregateType":"subscription","aggregateId":"sub-456","payload":{"subscriptionId":"sub-456","userId":"usr-789","userEmail":"test@example.com","planId":"plan-basic","currentPeriodStart":"2024-01-15","currentPeriodEnd":"2024-02-15"}}`

		env, err := Parse([]byte(body), logger)
		if err != nil {
			t.Fatalf("Parse() error = %v", err)
		}
		if env.EventID != "evt-123" {
			t.Errorf("EventID = %v, want evt-123", env.EventID)
		}
		if env.EventType != TypeSubscriptionActivated {
			t.Errorf("EventType = %v, want %v", env.EventType, TypeSubscriptionActivated)
		}
		if len(env.Payload) == 0 {
			t.Error("Payload is empty")
		}
	})

	t.Run("occurredAt parsed correctly", func(t *testing.T) {
		body := `{"eventId":"evt-123","schemaVersion":1,"occurredAt":"2024-01-15T10:30:00Z","eventType":"subscription.activated","aggregateType":"subscription","aggregateId":"sub-456","payload":{}}`

		env, err := Parse([]byte(body), logger)
		if err != nil {
			t.Fatalf("Parse() error = %v", err)
		}
		expected := time.Date(2024, 1, 15, 10, 30, 0, 0, time.UTC)
		if !env.OccurredAt.Equal(expected) {
			t.Errorf("OccurredAt = %v, want %v", env.OccurredAt, expected)
		}
	})
}
