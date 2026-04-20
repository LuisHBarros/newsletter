package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/assine/newsletter/access/permissions"
	"github.com/aws/aws-lambda-go/events"
	"go.uber.org/zap"
)

type fakeStore struct {
	perms         map[string]*permissions.Permission
	grantErr      error
	revokeErr     error
	checkErr      error
	listErr       error
	revokeExisted bool
}

func newFakeStore() *fakeStore {
	return &fakeStore{
		perms: make(map[string]*permissions.Permission),
	}
}

func (f *fakeStore) key(userID, contentID string) string {
	return userID + ":" + contentID
}

func (f *fakeStore) Grant(_ context.Context, perm *permissions.Permission) error {
	if f.grantErr != nil {
		return f.grantErr
	}
	perm.GrantedAt = time.Now().UTC()
	f.perms[f.key(perm.UserID, perm.ContentID)] = perm
	return nil
}

func (f *fakeStore) Revoke(_ context.Context, userID, contentID string) (bool, error) {
	if f.revokeErr != nil {
		return false, f.revokeErr
	}
	k := f.key(userID, contentID)
	_, existed := f.perms[k]
	delete(f.perms, k)
	f.revokeExisted = existed
	return existed, nil
}

func (f *fakeStore) Check(_ context.Context, userID, contentID string) (*permissions.Permission, error) {
	if f.checkErr != nil {
		return nil, f.checkErr
	}
	p, ok := f.perms[f.key(userID, contentID)]
	if !ok {
		return nil, nil
	}
	if p.ExpiresAt != nil && time.Now().UTC().After(*p.ExpiresAt) {
		return nil, nil
	}
	return p, nil
}

func (f *fakeStore) ListByUser(_ context.Context, userID string) ([]permissions.Permission, error) {
	if f.listErr != nil {
		return nil, f.listErr
	}
	var result []permissions.Permission
	now := time.Now().UTC()
	for _, p := range f.perms {
		if p.UserID == userID {
			if p.ExpiresAt == nil || now.Before(*p.ExpiresAt) {
				result = append(result, *p)
			}
		}
	}
	return result, nil
}

func testHandler(store Storer) *Handler {
	return NewHandler(store, zap.NewNop())
}

func adminCtx() events.APIGatewayProxyRequest {
	return events.APIGatewayProxyRequest{
		RequestContext: events.APIGatewayProxyRequestContext{
			Authorizer: map[string]interface{}{
				"claims": map[string]interface{}{
					"sub":            "admin-user-123",
					"scope":          "content:admin",
					"cognito:groups": []interface{}{"admin"},
				},
			},
		},
	}
}

func userCtx(sub string) events.APIGatewayProxyRequest {
	return events.APIGatewayProxyRequest{
		RequestContext: events.APIGatewayProxyRequestContext{
			Authorizer: map[string]interface{}{
				"claims": map[string]interface{}{
					"sub": sub,
				},
			},
		},
	}
}

func noAuthCtx() events.APIGatewayProxyRequest {
	return events.APIGatewayProxyRequest{
		RequestContext: events.APIGatewayProxyRequestContext{},
	}
}

func TestParseDuration(t *testing.T) {
	tests := []struct {
		input   string
		want    time.Duration
		wantErr bool
	}{
		{"30d", 30 * 24 * time.Hour, false},
		{"7d", 7 * 24 * time.Hour, false},
		{"1d", 24 * time.Hour, false},
		{"365d", 365 * 24 * time.Hour, false},
		{"1h", time.Hour, false},
		{"24h", 24 * time.Hour, false},
		{"30m", 30 * time.Minute, false},
		{"90s", 90 * time.Second, false},
		{"2h30m", 2*time.Hour + 30*time.Minute, false},
		{"0d", 0, true},
		{"-1d", 0, true},
		{"-30d", 0, true},
		{"-5h", 0, true},
		{"0h", 0, true},
		{"", 0, true},
		{"abc", 0, true},
		{"5min", 0, true},
		{"10000d", 0, true},
		{"0", 0, true},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got, err := parseDuration(tt.input)
			if tt.wantErr {
				if err == nil {
					t.Errorf("parseDuration(%q) = %v, want error", tt.input, got)
				}
				return
			}
			if err != nil {
				t.Errorf("parseDuration(%q) unexpected error: %v", tt.input, err)
				return
			}
			if got != tt.want {
				t.Errorf("parseDuration(%q) = %v, want %v", tt.input, got, tt.want)
			}
		})
	}
}

func TestValidateRequest(t *testing.T) {
	h := testHandler(newFakeStore())

	tests := []struct {
		name    string
		req     Request
		wantErr bool
	}{
		{"valid grant", Request{Action: ActionGrant, UserID: "u1", ContentID: "c1"}, false},
		{"valid check", Request{Action: ActionCheck, UserID: "u1", ContentID: "c1"}, false},
		{"valid list", Request{Action: ActionList, UserID: "u1"}, false},
		{"missing action", Request{UserID: "u1"}, true},
		{"missing userId", Request{Action: ActionGrant}, true},
		{"grant missing contentId", Request{Action: ActionGrant, UserID: "u1"}, true},
		{"check missing contentId", Request{Action: ActionCheck, UserID: "u1"}, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := h.validateRequest(&tt.req)
			if (err != nil) != tt.wantErr {
				t.Errorf("validateRequest() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestGrant_Forever_NoTTL(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionGrant,
		UserID:    "user1",
		ContentID: "content1",
		Duration:  "forever",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success, got: %s", resp.Error)
	}

	perm := store.perms[store.key("user1", "content1")]
	if perm == nil {
		t.Fatal("permission not stored")
	}
	if perm.ExpiresAt != nil {
		t.Errorf("ExpiresAt should be nil for forever grants, got %v", perm.ExpiresAt)
	}
	if perm.TTL != 0 {
		t.Errorf("TTL should be 0 for forever grants, got %d", perm.TTL)
	}
}

func TestGrant_WithDuration_SetsTTL(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionGrant,
		UserID:    "user1",
		ContentID: "content1",
		Duration:  "30d",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success, got: %s", resp.Error)
	}

	perm := store.perms[store.key("user1", "content1")]
	if perm == nil {
		t.Fatal("permission not stored")
	}
	if perm.ExpiresAt == nil {
		t.Fatal("ExpiresAt should be set for time-boxed grants")
	}
	expectedExpiry := time.Now().UTC().Add(30 * 24 * time.Hour)
	delta := expectedExpiry.Sub(*perm.ExpiresAt)
	if delta < 0 {
		delta = -delta
	}
	if delta > 5*time.Second {
		t.Errorf("ExpiresAt should be ~30d from now, got delta %v", delta)
	}
}

func TestGrant_DBError_ReturnsInternal(t *testing.T) {
	store := newFakeStore()
	store.grantErr = fmt.Errorf("dynamo throttle")
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionGrant,
		UserID:    "user1",
		ContentID: "content1",
	})
	if err == nil {
		t.Fatal("expected error from Handle")
	}
	if !errors.Is(err, ErrInternal) {
		t.Errorf("expected ErrInternal, got: %v", err)
	}
	if resp.Success {
		t.Error("expected failure response")
	}
}

func TestRevoke_Existing(t *testing.T) {
	store := newFakeStore()
	store.perms[store.key("user1", "content1")] = &permissions.Permission{
		UserID:    "user1",
		ContentID: "content1",
	}
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionRevoke,
		UserID:    "user1",
		ContentID: "content1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success, got: %s", resp.Error)
	}
	if !store.revokeExisted {
		t.Error("expected permission to have existed")
	}
}

func TestRevoke_NonExistent(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionRevoke,
		UserID:    "user1",
		ContentID: "content1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success (idempotent), got: %s", resp.Error)
	}
	if store.revokeExisted {
		t.Error("permission should not have existed")
	}
}

func TestCheck_NoPermission(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionCheck,
		UserID:    "user1",
		ContentID: "content1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success, got: %s", resp.Error)
	}
	if resp.HasAccess == nil || *resp.HasAccess {
		t.Error("expected HasAccess=false")
	}
}

func TestCheck_ExpiredPermission(t *testing.T) {
	store := newFakeStore()
	past := time.Now().UTC().Add(-1 * time.Hour)
	store.perms[store.key("user1", "content1")] = &permissions.Permission{
		UserID:    "user1",
		ContentID: "content1",
		ExpiresAt: &past,
	}
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action:    ActionCheck,
		UserID:    "user1",
		ContentID: "content1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.HasAccess == nil || *resp.HasAccess {
		t.Error("expired permission should not grant access")
	}
}

func TestListByUser(t *testing.T) {
	store := newFakeStore()
	store.perms[store.key("user1", "c1")] = &permissions.Permission{UserID: "user1", ContentID: "c1"}
	store.perms[store.key("user1", "c2")] = &permissions.Permission{UserID: "user1", ContentID: "c2"}
	store.perms[store.key("user2", "c1")] = &permissions.Permission{UserID: "user2", ContentID: "c1"}
	h := testHandler(store)

	resp, err := h.Handle(context.Background(), Request{
		Action: ActionList,
		UserID: "user1",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success, got: %s", resp.Error)
	}
	if len(resp.Permissions) != 2 {
		t.Errorf("expected 2 permissions, got %d", len(resp.Permissions))
	}
}

func TestAuth_ExtractCaller(t *testing.T) {
	t.Run("valid claims", func(t *testing.T) {
		req := userCtx("sub-123")
		caller, err := extractCaller(req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if caller.Sub != "sub-123" {
			t.Errorf("expected sub=sub-123, got %s", caller.Sub)
		}
		if caller.IsAdmin {
			t.Error("should not be admin")
		}
	})

	t.Run("admin via scope", func(t *testing.T) {
		req := adminCtx()
		caller, err := extractCaller(req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if !caller.IsAdmin {
			t.Error("expected admin")
		}
	})

	t.Run("no authorizer", func(t *testing.T) {
		req := noAuthCtx()
		_, err := extractCaller(req)
		if err == nil {
			t.Fatal("expected error")
		}
		if !errors.Is(err, ErrUnauthorized) {
			t.Errorf("expected ErrUnauthorized, got: %v", err)
		}
	})
}

func TestAuth_AuthorizeAction(t *testing.T) {
	tests := []struct {
		name    string
		caller  *callerIdentity
		req     Request
		wantErr bool
	}{
		{"admin can grant any user", &callerIdentity{Sub: "admin", IsAdmin: true}, Request{Action: ActionGrant, UserID: "other"}, false},
		{"user can grant self", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionGrant, UserID: "user1", ContentID: "c1"}, false},
		{"user cannot grant other", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionGrant, UserID: "user2", ContentID: "c1"}, true},
		{"user can check self", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionCheck, UserID: "user1", ContentID: "c1"}, false},
		{"user cannot check other", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionCheck, UserID: "user2", ContentID: "c1"}, true},
		{"user can list self", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionList, UserID: "user1"}, false},
		{"user cannot list other", &callerIdentity{Sub: "user1", IsAdmin: false}, Request{Action: ActionList, UserID: "user2"}, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			h := testHandler(newFakeStore())
			err := h.authorizeAction(tt.caller, &tt.req)
			if (err != nil) != tt.wantErr {
				t.Errorf("authorizeAction() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestHandleAPIGateway_AuthFlow(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	t.Run("no auth returns 401", func(t *testing.T) {
		body, _ := json.Marshal(Request{Action: ActionCheck, UserID: "u1", ContentID: "c1"})
		req := noAuthCtx()
		req.Body = string(body)

		resp, err := h.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 401 {
			t.Errorf("expected 401, got %d", resp.StatusCode)
		}
	})

	t.Run("wrong user returns 403", func(t *testing.T) {
		body, _ := json.Marshal(Request{Action: ActionGrant, UserID: "other-user", ContentID: "c1"})
		req := userCtx("my-user")
		req.Body = string(body)

		resp, err := h.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 403 {
			t.Errorf("expected 403, got %d", resp.StatusCode)
		}
	})

	t.Run("admin can grant for any user", func(t *testing.T) {
		body, _ := json.Marshal(Request{Action: ActionGrant, UserID: "any-user", ContentID: "c1", Duration: "30d"})
		req := adminCtx()
		req.Body = string(body)

		resp, err := h.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 200 {
			t.Errorf("expected 200, got %d: %s", resp.StatusCode, resp.Body)
		}
	})

	t.Run("db error returns 500", func(t *testing.T) {
		errStore := newFakeStore()
		errStore.checkErr = fmt.Errorf("dynamo down")
		hErr := testHandler(errStore)

		body, _ := json.Marshal(Request{Action: ActionCheck, UserID: "u1", ContentID: "c1"})
		req := adminCtx()
		req.Body = string(body)

		resp, err := hErr.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 500 {
			t.Errorf("expected 500, got %d", resp.StatusCode)
		}
	})

	t.Run("invalid JSON returns 400", func(t *testing.T) {
		req := adminCtx()
		req.Body = "not json"

		resp, err := h.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 400 {
			t.Errorf("expected 400, got %d", resp.StatusCode)
		}
	})

	t.Run("validation error returns 400", func(t *testing.T) {
		body, _ := json.Marshal(Request{Action: ActionGrant, UserID: ""})
		req := adminCtx()
		req.Body = string(body)

		resp, err := h.HandleAPIGateway(context.Background(), req)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.StatusCode != 400 {
			t.Errorf("expected 400, got %d", resp.StatusCode)
		}
	})
}

func TestGrant_Upsert(t *testing.T) {
	store := newFakeStore()
	h := testHandler(store)

	_, err := h.Handle(context.Background(), Request{
		Action: ActionGrant, UserID: "u1", ContentID: "c1", Duration: "7d",
	})
	if err != nil {
		t.Fatalf("first grant: %v", err)
	}

	resp, err := h.Handle(context.Background(), Request{
		Action: ActionGrant, UserID: "u1", ContentID: "c1", Duration: "30d",
	})
	if err != nil {
		t.Fatalf("re-grant (upsert): %v", err)
	}
	if !resp.Success {
		t.Errorf("upsert should succeed, got: %s", resp.Error)
	}
}

func TestListByUser_FiltersExpired(t *testing.T) {
	store := newFakeStore()
	past := time.Now().UTC().Add(-1 * time.Hour)
	future := time.Now().UTC().Add(24 * time.Hour)
	store.perms[store.key("u1", "expired")] = &permissions.Permission{UserID: "u1", ContentID: "expired", ExpiresAt: &past}
	store.perms[store.key("u1", "active")] = &permissions.Permission{UserID: "u1", ContentID: "active", ExpiresAt: &future}
	store.perms[store.key("u1", "forever")] = &permissions.Permission{UserID: "u1", ContentID: "forever"}

	h := testHandler(store)
	resp, err := h.Handle(context.Background(), Request{Action: ActionList, UserID: "u1"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(resp.Permissions) != 2 {
		t.Errorf("expected 2 active permissions (active + forever), got %d", len(resp.Permissions))
	}
}
