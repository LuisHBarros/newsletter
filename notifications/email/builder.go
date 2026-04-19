package email

import (
	"encoding/json"
	"fmt"

	"github.com/assine/newsletter/notifications/events"
	"go.uber.org/zap"
)

type EmailRequest struct {
	To           string
	TemplateName string
	Handler      string
	TemplateData map[string]string
}

func BuildRequest(env *events.Envelope, userEmailAddress string, templateName, handler string, logger *zap.Logger) *EmailRequest {
	data := extractTemplateData(env, logger)

	return &EmailRequest{
		To:           userEmailAddress,
		TemplateName: templateName,
		Handler:      handler,
		TemplateData: data,
	}
}

func extractTemplateData(env *events.Envelope, logger *zap.Logger) map[string]string {
	data := map[string]string{
		"eventId":       env.EventID,
		"eventType":     env.EventType,
		"occurredAt":    env.OccurredAt.Format("2006-01-02T15:04:05Z07:00"),
		"aggregateId":   env.AggregateID,
		"aggregateType": env.AggregateType,
	}

	switch env.EventType {
	case events.TypeSubscriptionActivated,
		events.TypeSubscriptionCanceled,
		events.TypeSubscriptionExpired:
		var p events.SubscriptionPayload
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			logger.Warn("failed to unmarshal subscription payload", zap.Error(err), zap.String("eventType", env.EventType))
		} else {
			data["subscriptionId"] = p.SubscriptionID
			data["userId"] = p.UserID
			data["userEmail"] = p.UserEmail
			data["planId"] = p.PlanID
			if p.CurrentPeriodStart != "" {
				data["currentPeriodStart"] = p.CurrentPeriodStart
			}
			if p.CurrentPeriodEnd != "" {
				data["currentPeriodEnd"] = p.CurrentPeriodEnd
			}
		}

	case events.TypeSubscriptionPastDue:
		var p events.PastDuePayload
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			logger.Warn("failed to unmarshal past_due payload", zap.Error(err), zap.String("eventType", env.EventType))
		} else {
			data["subscriptionId"] = p.SubscriptionID
			data["userId"] = p.UserID
			data["userEmail"] = p.UserEmail
			if p.Attempts > 0 {
				data["attempts"] = fmt.Sprintf("%d", p.Attempts)
			}
			if p.NextRetryAt != "" {
				data["nextRetryAt"] = p.NextRetryAt
			}
			if p.Reason != "" {
				data["reason"] = p.Reason
			}
		}

	case events.TypeIssuePublished, events.TypeIssueUpdated:
		var p events.IssuePayload
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			logger.Warn("failed to unmarshal issue payload", zap.Error(err), zap.String("eventType", env.EventType))
		} else {
			data["newsletterId"] = p.NewsletterID
			data["newsletterSlug"] = p.NewsletterSlug
			data["issueId"] = p.IssueID
			data["title"] = p.Title
			data["slug"] = p.Slug
			data["htmlS3Key"] = p.HtmlS3Key
			if p.PublishedAt != "" {
				data["publishedAt"] = p.PublishedAt
			}
			if p.Version > 0 {
				data["version"] = fmt.Sprintf("%d", p.Version)
			}
		}
	}

	return data
}
