package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"time"

	"github.com/assine/newsletter/access/permissions"
	"github.com/assine/newsletter/access/tracing"
	"github.com/aws/aws-lambda-go/events"
	"go.uber.org/zap"
)

var (
	ErrInvalidRequest = errors.New("invalid request")
	ErrUnauthorized   = errors.New("unauthorized")
	ErrAccessDenied   = errors.New("access denied")
	ErrInternal       = errors.New("internal error")
)

type Action string

const (
	ActionGrant  Action = "grant"
	ActionRevoke Action = "revoke"
	ActionCheck  Action = "check"
	ActionList   Action = "list"
)

type Request struct {
	Action    Action `json:"action"`
	UserID    string `json:"userId"`
	ContentID string `json:"contentId,omitempty"`
	PlanID    string `json:"planId,omitempty"`
	Duration  string `json:"duration,omitempty"`
}

type Response struct {
	Success     bool                     `json:"success"`
	HasAccess   *bool                    `json:"hasAccess,omitempty"`
	Permission  *permissions.Permission  `json:"permission,omitempty"`
	Permissions []permissions.Permission `json:"permissions,omitempty"`
	Error       string                   `json:"error,omitempty"`
}

type Handler struct {
	store  Storer
	logger *zap.Logger
}

type Storer interface {
	Grant(ctx context.Context, perm *permissions.Permission) error
	Revoke(ctx context.Context, userID, contentID string) (bool, error)
	Check(ctx context.Context, userID, contentID string) (*permissions.Permission, error)
	ListByUser(ctx context.Context, userID string) ([]permissions.Permission, error)
}

func NewHandler(store Storer, logger *zap.Logger) *Handler {
	return &Handler{
		store:  store,
		logger: logger,
	}
}

type callerIdentity struct {
	Sub     string
	IsAdmin bool
}

func extractCaller(request events.APIGatewayProxyRequest) (*callerIdentity, error) {
	authCtx := request.RequestContext.Authorizer
	if authCtx == nil {
		return nil, fmt.Errorf("%w: missing authorizer context", ErrUnauthorized)
	}

	claims, ok := authCtx["claims"].(map[string]interface{})
	if !ok {
		claimsRaw, ok := authCtx["claims"]
		if !ok {
			return nil, fmt.Errorf("%w: missing claims in authorizer", ErrUnauthorized)
		}
		claims, ok = claimsRaw.(map[string]interface{})
		if !ok {
			return nil, fmt.Errorf("%w: invalid claims format", ErrUnauthorized)
		}
	}

	sub, _ := claims["sub"].(string)
	if sub == "" {
		return nil, fmt.Errorf("%w: missing sub claim", ErrUnauthorized)
	}

	scope, _ := claims["scope"].(string)
	isAdmin := containsScope(scope, "content:admin")

	cognitoGroups, _ := claims["cognito:groups"].([]interface{})
	for _, g := range cognitoGroups {
		if s, ok := g.(string); ok && s == "admin" {
			isAdmin = true
		}
	}

	return &callerIdentity{Sub: sub, IsAdmin: isAdmin}, nil
}

func containsScope(scopeStr, target string) bool {
	for _, s := range splitScopes(scopeStr) {
		if s == target {
			return true
		}
	}
	return false
}

func splitScopes(s string) []string {
	if s == "" {
		return nil
	}
	var result []string
	start := 0
	for i := 0; i <= len(s); i++ {
		if i == len(s) || s[i] == ' ' {
			if i > start {
				result = append(result, s[start:i])
			}
			start = i + 1
		}
	}
	return result
}

func (h *Handler) authorizeAction(caller *callerIdentity, req *Request) error {
	if caller.IsAdmin {
		return nil
	}
	switch req.Action {
	case ActionGrant, ActionRevoke:
		if caller.Sub != req.UserID {
			return fmt.Errorf("%w: can only %s for yourself (caller=%s, target=%s)", ErrAccessDenied, req.Action, caller.Sub, req.UserID)
		}
	case ActionCheck:
		if caller.Sub != req.UserID {
			return fmt.Errorf("%w: can only check your own permissions", ErrAccessDenied)
		}
	case ActionList:
		if caller.Sub != req.UserID {
			return fmt.Errorf("%w: can only list your own permissions", ErrAccessDenied)
		}
	}
	return nil
}

func (h *Handler) Handle(ctx context.Context, req Request) (Response, error) {
	ctx, segClose := tracing.BeginSubsegment(ctx, "access-handler")
	defer segClose()

	if err := h.validateRequest(&req); err != nil {
		return Response{Success: false, Error: err.Error()}, nil
	}

	h.logger.Info("processing access request",
		zap.String("action", string(req.Action)),
		zap.String("userId", req.UserID),
	)

	switch req.Action {
	case ActionGrant:
		return h.handleGrant(ctx, req)
	case ActionRevoke:
		return h.handleRevoke(ctx, req)
	case ActionCheck:
		return h.handleCheck(ctx, req)
	case ActionList:
		return h.handleList(ctx, req)
	default:
		return Response{Success: false, Error: fmt.Sprintf("unknown action: %s", req.Action)}, nil
	}
}

func (h *Handler) validateRequest(req *Request) error {
	if req.Action == "" {
		return fmt.Errorf("%w: action is required", ErrInvalidRequest)
	}
	if req.UserID == "" {
		return fmt.Errorf("%w: userId is required", ErrInvalidRequest)
	}
	if req.Action == ActionGrant || req.Action == ActionRevoke || req.Action == ActionCheck {
		if req.ContentID == "" {
			return fmt.Errorf("%w: contentId is required for %s", ErrInvalidRequest, req.Action)
		}
	}
	return nil
}

func (h *Handler) handleGrant(ctx context.Context, req Request) (Response, error) {
	ctx, segClose := tracing.BeginSubsegment(ctx, "grant-permission")
	defer segClose()

	perm := &permissions.Permission{
		UserID:    req.UserID,
		ContentID: req.ContentID,
		PlanID:    req.PlanID,
	}

	if req.Duration != "" && req.Duration != "forever" {
		duration, err := parseDuration(req.Duration)
		if err != nil {
			return Response{Success: false, Error: fmt.Sprintf("invalid duration: %v", err)}, nil
		}
		expiresAt := time.Now().UTC().Add(duration)
		perm.ExpiresAt = &expiresAt
	}

	if err := h.store.Grant(ctx, perm); err != nil {
		h.logger.Error("failed to grant permission",
			zap.String("userId", req.UserID),
			zap.String("contentId", req.ContentID),
			zap.Error(err),
		)
		return Response{}, fmt.Errorf("%w: grant failed: %v", ErrInternal, err)
	}

	h.logger.Info("permission granted",
		zap.String("userId", req.UserID),
		zap.String("contentId", req.ContentID),
	)

	return Response{Success: true, Permission: perm}, nil
}

func (h *Handler) handleRevoke(ctx context.Context, req Request) (Response, error) {
	ctx, segClose := tracing.BeginSubsegment(ctx, "revoke-permission")
	defer segClose()

	existed, err := h.store.Revoke(ctx, req.UserID, req.ContentID)
	if err != nil {
		h.logger.Error("failed to revoke permission",
			zap.String("userId", req.UserID),
			zap.String("contentId", req.ContentID),
			zap.Error(err),
		)
		return Response{}, fmt.Errorf("%w: revoke failed: %v", ErrInternal, err)
	}

	if existed {
		h.logger.Info("permission revoked",
			zap.String("userId", req.UserID),
			zap.String("contentId", req.ContentID),
		)
	} else {
		h.logger.Warn("revoke called on non-existent permission",
			zap.String("userId", req.UserID),
			zap.String("contentId", req.ContentID),
		)
	}

	return Response{Success: true}, nil
}

func (h *Handler) handleCheck(ctx context.Context, req Request) (Response, error) {
	ctx, segClose := tracing.BeginSubsegment(ctx, "check-permission")
	defer segClose()

	perm, err := h.store.Check(ctx, req.UserID, req.ContentID)
	if err != nil {
		h.logger.Error("failed to check permission",
			zap.String("userId", req.UserID),
			zap.String("contentId", req.ContentID),
			zap.Error(err),
		)
		return Response{}, fmt.Errorf("%w: check failed: %v", ErrInternal, err)
	}

	hasAccess := perm != nil
	resp := Response{
		Success:   true,
		HasAccess: &hasAccess,
	}
	if perm != nil {
		resp.Permission = perm
	}

	return resp, nil
}

func (h *Handler) handleList(ctx context.Context, req Request) (Response, error) {
	ctx, segClose := tracing.BeginSubsegment(ctx, "list-permissions")
	defer segClose()

	perms, err := h.store.ListByUser(ctx, req.UserID)
	if err != nil {
		h.logger.Error("failed to list permissions",
			zap.String("userId", req.UserID),
			zap.Error(err),
		)
		return Response{}, fmt.Errorf("%w: list failed: %v", ErrInternal, err)
	}

	return Response{Success: true, Permissions: perms}, nil
}

func (h *Handler) HandleAPIGateway(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	var req Request
	if err := json.Unmarshal([]byte(request.Body), &req); err != nil {
		return h.errorResponse(400, "invalid JSON body"), nil
	}

	caller, err := extractCaller(request)
	if err != nil {
		if errors.Is(err, ErrUnauthorized) {
			return h.errorResponse(401, "unauthorized"), nil
		}
		return h.errorResponse(403, err.Error()), nil
	}

	if err := h.authorizeAction(caller, &req); err != nil {
		return h.errorResponse(403, err.Error()), nil
	}

	resp, err := h.Handle(ctx, req)
	if err != nil {
		h.logger.Error("handler error", zap.Error(err))
		if errors.Is(err, ErrAccessDenied) {
			return h.errorResponse(403, "access denied"), nil
		}
		return h.errorResponse(500, "internal error"), nil
	}

	statusCode := 200
	if !resp.Success {
		statusCode = 400
	}

	body, _ := json.Marshal(resp)
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(body),
	}, nil
}

func (h *Handler) errorResponse(code int, message string) events.APIGatewayProxyResponse {
	body, _ := json.Marshal(Response{Success: false, Error: message})
	return events.APIGatewayProxyResponse{
		StatusCode: code,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(body),
	}
}

const maxDurationDays = 3650

func parseDuration(s string) (time.Duration, error) {
	if s == "" {
		return 0, fmt.Errorf("empty duration")
	}

	if len(s) >= 2 && s[len(s)-1] == 'd' {
		numStr := s[:len(s)-1]
		days, err := strconv.Atoi(numStr)
		if err != nil {
			return 0, fmt.Errorf("invalid day count: %q", numStr)
		}
		if days <= 0 {
			return 0, fmt.Errorf("duration must be positive, got %d", days)
		}
		if days > maxDurationDays {
			return 0, fmt.Errorf("duration exceeds maximum of %d days", maxDurationDays)
		}
		return time.Duration(days) * 24 * time.Hour, nil
	}

	d, err := time.ParseDuration(s)
	if err != nil {
		return 0, fmt.Errorf("invalid duration %q (supported: Nh, Nm, Ns, Nd)", s)
	}
	if d <= 0 {
		return 0, fmt.Errorf("duration must be positive, got %s", d)
	}
	return d, nil
}
