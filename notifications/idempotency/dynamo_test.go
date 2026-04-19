package idempotency

import (
	"context"
	"testing"
	"time"
)

// MockStore implements Store interface for testing
type MockStore struct {
	processed map[string]bool
	shouldErr bool
}

func NewMockStore() *MockStore {
	return &MockStore{
		processed: make(map[string]bool),
	}
}

func (m *MockStore) IsProcessed(ctx context.Context, eventID, handler string) (bool, error) {
	if m.shouldErr {
		return false, context.DeadlineExceeded
	}
	key := eventID + ":" + handler
	return m.processed[key], nil
}

func (m *MockStore) MarkProcessed(ctx context.Context, eventID, handler string) error {
	if m.shouldErr {
		return context.DeadlineExceeded
	}
	key := eventID + ":" + handler
	m.processed[key] = true
	return nil
}

func TestMockStore_IsProcessed(t *testing.T) {
	store := NewMockStore()
	ctx := context.Background()

	t.Run("new event not processed", func(t *testing.T) {
		processed, err := store.IsProcessed(ctx, "evt-1", "handler-1")
		if err != nil {
			t.Errorf("IsProcessed() error = %v", err)
		}
		if processed {
			t.Error("IsProcessed() = true for new event, want false")
		}
	})

	t.Run("marked event is processed", func(t *testing.T) {
		_ = store.MarkProcessed(ctx, "evt-2", "handler-1")
		processed, err := store.IsProcessed(ctx, "evt-2", "handler-1")
		if err != nil {
			t.Errorf("IsProcessed() error = %v", err)
		}
		if !processed {
			t.Error("IsProcessed() = false for marked event, want true")
		}
	})

	t.Run("different handlers are independent", func(t *testing.T) {
		_ = store.MarkProcessed(ctx, "evt-3", "handler-a")
		
		processedA, _ := store.IsProcessed(ctx, "evt-3", "handler-a")
		processedB, _ := store.IsProcessed(ctx, "evt-3", "handler-b")
		
		if !processedA {
			t.Error("handler-a should be marked")
		}
		if processedB {
			t.Error("handler-b should not be marked yet")
		}
	})
}

func TestMockStore_MarkProcessed(t *testing.T) {
	store := NewMockStore()
	ctx := context.Background()

	t.Run("mark processed succeeds", func(t *testing.T) {
		err := store.MarkProcessed(ctx, "evt-1", "handler-1")
		if err != nil {
			t.Errorf("MarkProcessed() error = %v", err)
		}

		processed, _ := store.IsProcessed(ctx, "evt-1", "handler-1")
		if !processed {
			t.Error("Event should be marked as processed")
		}
	})

	t.Run("mark same event twice is idempotent", func(t *testing.T) {
		_ = store.MarkProcessed(ctx, "evt-2", "handler-1")
		err := store.MarkProcessed(ctx, "evt-2", "handler-1")
		if err != nil {
			t.Errorf("Second MarkProcessed() error = %v", err)
		}
	})
}

func TestMockStore_Errors(t *testing.T) {
	store := NewMockStore()
	store.shouldErr = true
	ctx, cancel := context.WithTimeout(context.Background(), 1*time.Nanosecond)
	defer cancel()

	t.Run("IsProcessed returns error when store fails", func(t *testing.T) {
		_, err := store.IsProcessed(ctx, "evt-1", "handler-1")
		if err == nil {
			t.Error("IsProcessed() should return error when store fails")
		}
	})

	t.Run("MarkProcessed returns error when store fails", func(t *testing.T) {
		err := store.MarkProcessed(ctx, "evt-1", "handler-1")
		if err == nil {
			t.Error("MarkProcessed() should return error when store fails")
		}
	})
}
