package idempotency

import (
	"context"
	"errors"
	"testing"
	"time"
)

type MockStore struct {
	processed map[string]bool
	shouldErr bool
}

func NewMockStore() *MockStore {
	return &MockStore{
		processed: make(map[string]bool),
	}
}

func (m *MockStore) ClaimProcessing(ctx context.Context, eventID, handler string) error {
	if m.shouldErr {
		return context.DeadlineExceeded
	}
	key := eventID + ":" + handler
	if m.processed[key] {
		return ErrAlreadyProcessed
	}
	m.processed[key] = true
	return nil
}

func (m *MockStore) DeleteClaim(ctx context.Context, eventID, handler string) error {
	if m.shouldErr {
		return context.DeadlineExceeded
	}
	key := eventID + ":" + handler
	delete(m.processed, key)
	return nil
}

func TestMockStore_ClaimProcessing(t *testing.T) {
	store := NewMockStore()
	ctx := context.Background()

	t.Run("first claim succeeds", func(t *testing.T) {
		err := store.ClaimProcessing(ctx, "evt-1", "handler-1")
		if err != nil {
			t.Errorf("ClaimProcessing() error = %v", err)
		}
	})

	t.Run("duplicate claim returns ErrAlreadyProcessed", func(t *testing.T) {
		err := store.ClaimProcessing(ctx, "evt-1", "handler-1")
		if !errors.Is(err, ErrAlreadyProcessed) {
			t.Errorf("ClaimProcessing() second call = %v, want ErrAlreadyProcessed", err)
		}
	})

	t.Run("different handlers are independent", func(t *testing.T) {
		_ = store.ClaimProcessing(ctx, "evt-3", "handler-a")

		errA := store.ClaimProcessing(ctx, "evt-3", "handler-a")
		errB := store.ClaimProcessing(ctx, "evt-3", "handler-b")

		if !errors.Is(errA, ErrAlreadyProcessed) {
			t.Error("handler-a should already be claimed")
		}
		if errB != nil {
			t.Error("handler-b should not be claimed yet")
		}
	})
}

func TestMockStore_DeleteClaim(t *testing.T) {
	store := NewMockStore()
	ctx := context.Background()

	t.Run("delete allows re-claim", func(t *testing.T) {
		_ = store.ClaimProcessing(ctx, "evt-del", "handler-1")
		err := store.DeleteClaim(ctx, "evt-del", "handler-1")
		if err != nil {
			t.Errorf("DeleteClaim() error = %v", err)
		}
		err = store.ClaimProcessing(ctx, "evt-del", "handler-1")
		if err != nil {
			t.Errorf("ClaimProcessing() after delete = %v, want nil", err)
		}
	})
}

func TestMockStore_Errors(t *testing.T) {
	store := NewMockStore()
	store.shouldErr = true
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Nanosecond)
	defer cancel()

	t.Run("ClaimProcessing returns error when store fails", func(t *testing.T) {
		err := store.ClaimProcessing(ctx, "evt-1", "handler-1")
		if err == nil {
			t.Error("ClaimProcessing() should return error when store fails")
		}
	})

	t.Run("DeleteClaim returns error when store fails", func(t *testing.T) {
		err := store.DeleteClaim(ctx, "evt-1", "handler-1")
		if err == nil {
			t.Error("DeleteClaim() should return error when store fails")
		}
	})
}
