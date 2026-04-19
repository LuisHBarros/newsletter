package events

import (
	"encoding/json"
	"time"
)

const CurrentSchemaVersion = 1

type Envelope struct {
	EventID       string          `json:"eventId"`
	SchemaVersion int             `json:"schemaVersion"`
	OccurredAt    time.Time       `json:"occurredAt"`
	EventType     string          `json:"eventType"`
	AggregateType string          `json:"aggregateType"`
	AggregateID   string          `json:"aggregateId"`
	Payload       json.RawMessage `json:"payload"`
}

const (
	TypeSubscriptionActivated = "subscription.activated"
	TypeSubscriptionPastDue   = "subscription.past_due"
	TypeSubscriptionCanceled  = "subscription.canceled"
	TypeSubscriptionExpired   = "subscription.expired"
	TypeIssuePublished        = "content.issue.published"
	TypeIssueUpdated          = "content.issue.updated"
)

type SubscriptionPayload struct {
	SubscriptionID     string `json:"subscriptionId"`
	UserID             string `json:"userId"`
	UserEmail          string `json:"userEmail"`
	PlanID             string `json:"planId"`
	CurrentPeriodStart string `json:"currentPeriodStart,omitempty"`
	CurrentPeriodEnd   string `json:"currentPeriodEnd,omitempty"`
}

type PastDuePayload struct {
	SubscriptionID string `json:"subscriptionId"`
	UserID         string `json:"userId"`
	UserEmail      string `json:"userEmail"`
	Attempts       int    `json:"attempts,omitempty"`
	NextRetryAt    string `json:"nextRetryAt,omitempty"`
	Reason         string `json:"reason,omitempty"`
}

type IssuePayload struct {
	NewsletterID   string   `json:"newsletterId"`
	NewsletterSlug string   `json:"newsletterSlug"`
	IssueID        string   `json:"issueId"`
	Version        int      `json:"version,omitempty"`
	Title          string   `json:"title"`
	Slug           string   `json:"slug"`
	HtmlS3Key      string   `json:"htmlS3Key"`
	PublishedAt    string   `json:"publishedAt"`
	PlanIDs        []string `json:"planIds"`
}
