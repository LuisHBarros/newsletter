package email

import (
	"testing"
	"time"

	"github.com/assine/newsletter/notifications/events"
	"go.uber.org/zap"
)

func TestBuildRequest(t *testing.T) {
	logger := zap.NewNop()

	tests := []struct {
		name             string
		env              *events.Envelope
		userEmailAddress string
		templateName     string
		handler          string
		wantFields       map[string]string
	}{
		{
			name: "subscription activated builds correct request",
			env: &events.Envelope{
				EventID:       "evt-123",
				EventType:     events.TypeSubscriptionActivated,
				OccurredAt:    time.Date(2024, 1, 15, 10, 30, 0, 0, time.UTC),
				AggregateID:   "sub-456",
				AggregateType: "subscription",
				Payload:       []byte(`{"subscriptionId":"sub-456","userId":"usr-789","userEmail":"user@example.com","planId":"plan-basic"}`),
			},
			userEmailAddress: "user@example.com",
			templateName:     "subscription-activated",
			handler:          "notify-subscription-activated",
			wantFields: map[string]string{
				"eventId":        "evt-123",
				"eventType":      "subscription.activated",
				"aggregateId":    "sub-456",
				"subscriptionId": "sub-456",
				"userId":         "usr-789",
				"userEmail":      "user@example.com",
				"planId":         "plan-basic",
			},
		},
		{
			name: "subscription past due builds correct request",
			env: &events.Envelope{
				EventID:       "evt-456",
				EventType:     events.TypeSubscriptionPastDue,
				OccurredAt:    time.Date(2024, 1, 15, 10, 30, 0, 0, time.UTC),
				AggregateID:   "sub-789",
				AggregateType: "subscription",
				Payload:       []byte(`{"subscriptionId":"sub-789","userId":"usr-101","userEmail":"pastdue@example.com","attempts":3,"nextRetryAt":"2024-01-16T10:00:00Z","reason":"card_declined"}`),
			},
			userEmailAddress: "pastdue@example.com",
			templateName:     "subscription-past-due",
			handler:          "notify-subscription-past-due",
			wantFields: map[string]string{
				"eventId":        "evt-456",
				"eventType":      "subscription.past_due",
				"subscriptionId": "sub-789",
				"userId":         "usr-101",
				"attempts":       "3",
				"nextRetryAt":    "2024-01-16T10:00:00Z",
				"reason":         "card_declined",
			},
		},
		{
			name: "issue published builds correct request",
			env: &events.Envelope{
				EventID:       "evt-789",
				EventType:     events.TypeIssuePublished,
				OccurredAt:    time.Date(2024, 1, 15, 12, 0, 0, 0, time.UTC),
				AggregateID:   "iss-123",
				AggregateType: "issue",
				Payload:       []byte(`{"newsletterId":"nl-456","newsletterSlug":"tech-weekly","issueId":"iss-123","title":"Weekly Roundup","slug":"weekly-roundup","htmlS3Key":"issues/iss-123.html","publishedAt":"2024-01-15T12:00:00Z","version":1,"planIds":["plan-1","plan-2"]}`),
			},
			userEmailAddress: "subscriber@example.com",
			templateName:     "issue-published",
			handler:          "notify-issue-published",
			wantFields: map[string]string{
				"eventId":      "evt-789",
				"eventType":    "content.issue.published",
				"newsletterId": "nl-456",
				"issueId":      "iss-123",
				"title":        "Weekly Roundup",
				"version":      "1",
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := BuildRequest(tt.env, tt.userEmailAddress, tt.templateName, tt.handler, logger)

			if req == nil {
				t.Fatal("BuildRequest() returned nil")
			}
			if req.To != tt.userEmailAddress {
				t.Errorf("To = %v, want %v", req.To, tt.userEmailAddress)
			}
			if req.TemplateName != tt.templateName {
				t.Errorf("TemplateName = %v, want %v", req.TemplateName, tt.templateName)
			}
			if req.Handler != tt.handler {
				t.Errorf("Handler = %v, want %v", req.Handler, tt.handler)
			}

			for key, wantValue := range tt.wantFields {
				if gotValue, ok := req.TemplateData[key]; !ok || gotValue != wantValue {
					t.Errorf("TemplateData[%q] = %v, want %v", key, gotValue, wantValue)
				}
			}
		})
	}
}

func TestExtractTemplateData_InvalidPayload(t *testing.T) {
	logger := zap.NewNop()

	t.Run("invalid subscription payload logs warning but continues", func(t *testing.T) {
		env := &events.Envelope{
			EventID:       "evt-123",
			EventType:     events.TypeSubscriptionActivated,
			OccurredAt:    time.Date(2024, 1, 15, 10, 30, 0, 0, time.UTC),
			AggregateID:   "sub-456",
			AggregateType: "subscription",
			Payload:       []byte(`{invalid json`),
		}

		req := BuildRequest(env, "test@example.com", "template", "handler", logger)
		if req == nil {
			t.Fatal("BuildRequest() returned nil on invalid payload")
		}

		// Should still have base fields
		if req.TemplateData["eventId"] != "evt-123" {
			t.Error("Missing eventId in template data")
		}
		// Should not have subscription-specific fields
		if _, hasSubId := req.TemplateData["subscriptionId"]; hasSubId {
			t.Error("subscriptionId should not be present with invalid payload")
		}
	})
}
