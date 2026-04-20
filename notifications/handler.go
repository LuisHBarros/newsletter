package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"sync/atomic"
	"time"

	"github.com/assine/newsletter/notifications/email"
	"github.com/assine/newsletter/notifications/events"
	"github.com/assine/newsletter/notifications/idempotency"
	"github.com/assine/newsletter/notifications/metrics"
	"github.com/assine/newsletter/notifications/subscriptions"
	"github.com/assine/newsletter/notifications/tracing"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
)

var ErrParseFail = errors.New("event parse failed")

type Handler struct {
	email         *email.Client
	idempotency   *idempotency.Store
	subscriptions *subscriptions.Client
	prefix        string
	logger        *zap.Logger
}

func NewHandler(emailClient *email.Client, idempotencyStore *idempotency.Store, subscriptionsClient *subscriptions.Client, prefix string, logger *zap.Logger) *Handler {
	return &Handler{
		email:         emailClient,
		idempotency:   idempotencyStore,
		subscriptions: subscriptionsClient,
		prefix:        prefix,
		logger:        logger,
	}
}

func (h *Handler) Handle(ctx context.Context, recordBody []byte) error {
	start := time.Now()

	env, err := events.Parse(recordBody, h.logger)
	if err != nil {
		return fmt.Errorf("%w: %v", ErrParseFail, err)
	}

	if len(env.Payload) == 0 {
		h.logger.Warn("unsupported event type or empty payload, skipping",
			zap.String("eventType", env.EventType),
			zap.String("eventId", env.EventID),
		)
		return nil
	}

	templateName, handler, ok := email.ResolveTemplate(env.EventType, h.prefix, env.SchemaVersion)
	if !ok {
		h.logger.Warn("no email template mapped for event type, skipping",
			zap.String("eventType", env.EventType),
			zap.String("eventId", env.EventID),
		)
		return nil
	}

	ctx, segClose := tracing.BeginSubsegment(ctx, "claim-processing")
	err = h.idempotency.ClaimProcessing(ctx, env.EventID, handler)
	segClose()
	if err != nil {
		if errors.Is(err, idempotency.ErrAlreadyProcessed) {
			h.logger.Info("event already processed, skipping",
				zap.String("eventId", env.EventID),
				zap.String("handler", handler),
			)
			return nil
		}
		return fmt.Errorf("claim processing: %w", err)
	}

	var handleErr error
	if env.EventType == events.TypeIssuePublished || env.EventType == events.TypeIssueUpdated {
		handleErr = h.handleFanOut(ctx, env, templateName, handler)
	} else {
		userEmail, err := resolveUserEmail(env)
		if err != nil {
			return err
		}
		handleErr = h.sendEmail(ctx, env, userEmail, templateName, handler)
	}

	metrics.RecordProcessingDuration(h.logger, env.EventType, float64(time.Since(start).Milliseconds()))

	return handleErr
}

// sendEmail sends a single email and marks the event as processed.
func (h *Handler) sendEmail(ctx context.Context, env *events.Envelope, to, templateName, handler string) error {
	req := email.BuildRequest(env, to, templateName, handler, h.logger)

	ctx, segClose := tracing.BeginSubsegment(ctx, "ses-send-email")
	if err := h.email.SendTemplated(ctx, req.To, req.TemplateName, req.TemplateData); err != nil {
		segClose()
		metrics.RecordEmailFailed(h.logger, env.EventType)
		if delErr := h.idempotency.DeleteClaim(ctx, env.EventID, req.Handler); delErr != nil {
			h.logger.Warn("failed to delete claim after send failure",
				zap.String("eventId", env.EventID),
				zap.String("handler", req.Handler),
				zap.Error(delErr),
			)
		}
		return fmt.Errorf("send email: %w", err)
	}
	segClose()

	h.logger.Info("email sent",
		zap.String("eventId", env.EventID),
		zap.String("eventType", env.EventType),
		zap.String("handler", handler),
		zap.String("template", req.TemplateName),
		zap.String("to", req.To),
	)

	metrics.RecordEmailSent(h.logger, env.EventType)

	return nil
}

// handleFanOut sends emails to all active subscribers for issue events.
func (h *Handler) handleFanOut(ctx context.Context, env *events.Envelope, templateName, handler string) error {
	var payload events.IssuePayload
	if err := json.Unmarshal(env.Payload, &payload); err != nil {
		return fmt.Errorf("unmarshal issue payload: %w", err)
	}

	if len(payload.PlanIDs) == 0 {
		h.logger.Warn("issue event has no plan IDs, skipping",
			zap.String("eventId", env.EventID),
			zap.String("issueId", payload.IssueID),
		)
		return nil
	}

	ctx, segClose := tracing.BeginSubsegment(ctx, "fetch-subscribers")
	subscribers, err := h.subscriptions.FindActiveSubscribers(ctx, payload.PlanIDs)
	segClose()
	if err != nil {
		return fmt.Errorf("fetch subscribers: %w", err)
	}

	if len(subscribers) == 0 {
		h.logger.Info("no active subscribers for issue",
			zap.String("eventId", env.EventID),
			zap.String("issueId", payload.IssueID),
		)
		return nil
	}

	// Process subscribers concurrently with limited parallelism
	const maxConcurrency = 10
	var failedCount int32
	g, ctx := errgroup.WithContext(ctx)
	sem := make(chan struct{}, maxConcurrency)

	for _, sub := range subscribers {
		sub := sub        // capture loop variable
		sem <- struct{}{} // acquire before spawning
		g.Go(func() error {
			defer func() { <-sem }()

			// Check idempotency per subscriber (unique key: eventID + userID)
			subscriberHandler := handler + ":" + sub.UserID

			ctx, segClose := tracing.BeginSubsegment(ctx, "claim-processing")
			err := h.idempotency.ClaimProcessing(ctx, env.EventID, subscriberHandler)
			segClose()
			if err != nil {
				if errors.Is(err, idempotency.ErrAlreadyProcessed) {
					h.logger.Info("already sent to subscriber, skipping",
						zap.String("eventId", env.EventID),
						zap.String("userId", sub.UserID),
					)
					return nil
				}
				h.logger.Error("claim processing failed",
					zap.String("eventId", env.EventID),
					zap.String("userId", sub.UserID),
					zap.Error(err),
				)
				atomic.AddInt32(&failedCount, 1)
				return nil
			}

			if err := h.sendEmail(ctx, env, sub.UserEmail, templateName, subscriberHandler); err != nil {
				h.logger.Error("failed to send email to subscriber",
					zap.String("eventId", env.EventID),
					zap.String("userId", sub.UserID),
					zap.String("email", sub.UserEmail),
					zap.Error(err),
				)
				atomic.AddInt32(&failedCount, 1)
			}
			return nil
		})
	}

	if err := g.Wait(); err != nil {
		return fmt.Errorf("fan-out processing error: %w", err)
	}

	if failedCount > 0 {
		h.logger.Warn("fan-out partially failed",
			zap.Int32("failedCount", failedCount),
			zap.Int("totalSubscribers", len(subscribers)),
		)
		// Don't return error - per-subscriber idempotency prevents duplicates on retry
		// SQS retry would just waste DB reads on already-processed subscribers
	}

	h.logger.Info("fan-out complete",
		zap.String("eventId", env.EventID),
		zap.String("eventType", env.EventType),
		zap.Int("subscriberCount", len(subscribers)),
	)

	metrics.RecordFanOutSubscribers(h.logger, env.EventType, len(subscribers))

	return nil
}

func resolveUserEmail(env *events.Envelope) (string, error) {
	switch env.EventType {
	case events.TypeSubscriptionActivated,
		events.TypeSubscriptionCanceled,
		events.TypeSubscriptionExpired:
		var p events.SubscriptionPayload
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			return "", fmt.Errorf("unmarshal subscription payload: %w", err)
		}
		if p.UserEmail == "" {
			return "", fmt.Errorf("missing userEmail in payload for event %s", env.EventType)
		}
		return p.UserEmail, nil

	case events.TypeSubscriptionPastDue:
		var p events.PastDuePayload
		if err := json.Unmarshal(env.Payload, &p); err != nil {
			return "", fmt.Errorf("unmarshal past_due payload: %w", err)
		}
		if p.UserEmail == "" {
			return "", fmt.Errorf("missing userEmail in payload for event %s", env.EventType)
		}
		return p.UserEmail, nil

	default:
		return "", fmt.Errorf("unknown event type: %s", env.EventType)
	}
}
