package email

import (
	"fmt"

	"github.com/assine/newsletter/notifications/events"
)

type mapping struct {
	template string
	handler  string
}

var templateMap = map[string]mapping{
	events.TypeSubscriptionActivated: {template: "welcome", handler: "email:welcome"},
	events.TypeSubscriptionPastDue:   {template: "payment_failed", handler: "email:payment_failed"},
	events.TypeSubscriptionCanceled:  {template: "canceled", handler: "email:canceled"},
	events.TypeSubscriptionExpired:   {template: "expired", handler: "email:expired"},
	// billing.invoice.receipt_removed removed - Stripe handles receipts natively
	events.TypeIssuePublished: {template: "newsletter_issue", handler: "email:newsletter_issue"},
	events.TypeIssueUpdated:   {template: "newsletter_errata", handler: "email:newsletter_errata"},
}

func ResolveTemplate(eventType, prefix string, schemaVersion int) (sesTemplateName, handler string, ok bool) {
	m, found := templateMap[eventType]
	if !found {
		return "", "", false
	}
	sesTemplateName = fmt.Sprintf("%s-%s-%d", prefix, m.template, schemaVersion)
	return sesTemplateName, m.handler, true
}
