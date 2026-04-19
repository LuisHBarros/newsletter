package subscriptions

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestNewClient(t *testing.T) {
	tests := []struct {
		name      string
		baseURL   string
		wantError bool
	}{
		{
			name:      "valid URL returns client",
			baseURL:   "http://localhost:8080",
			wantError: false,
		},
		{
			name:      "empty URL returns error",
			baseURL:   "",
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client, err := NewClient(tt.baseURL, "test-api-key")
			if (err != nil) != tt.wantError {
				t.Errorf("NewClient() error = %v, wantError %v", err, tt.wantError)
			}
			if !tt.wantError && client == nil {
				t.Error("NewClient() returned nil client without error")
			}
		})
	}
}

func TestFindActiveSubscribers(t *testing.T) {
	tests := []struct {
		name            string
		responseStatus  int
		responseBody    map[string]interface{}
		wantSubscribers int
		wantError       bool
	}{
		{
			name:           "single page of subscribers",
			responseStatus: http.StatusOK,
			responseBody: map[string]interface{}{
				"content": []map[string]string{
					{"userId": "usr-1", "userEmail": "user1@example.com"},
					{"userId": "usr-2", "userEmail": "user2@example.com"},
				},
				"totalPages": 1,
				"pageNumber": 0,
			},
			wantSubscribers: 2,
			wantError:       false,
		},
		{
			name:           "multiple pages - all fetched",
			responseStatus: http.StatusOK,
			responseBody: map[string]interface{}{
				"content": []map[string]string{
					{"userId": "usr-1", "userEmail": "user1@example.com"},
				},
				"totalPages": 1,
				"pageNumber": 0,
			},
			wantSubscribers: 1,
			wantError:       false,
		},
		{
			name:           "empty subscriber list",
			responseStatus: http.StatusOK,
			responseBody: map[string]interface{}{
				"content":    []map[string]string{},
				"totalPages": 1,
				"pageNumber": 0,
			},
			wantSubscribers: 0,
			wantError:       false,
		},
		{
			name:           "subscribers without email are filtered",
			responseStatus: http.StatusOK,
			responseBody: map[string]interface{}{
				"content": []map[string]string{
					{"userId": "usr-1", "userEmail": "user1@example.com"},
					{"userId": "usr-2", "userEmail": ""},
					{"userId": "usr-3", "userEmail": "user3@example.com"},
				},
				"totalPages": 1,
				"pageNumber": 0,
			},
			wantSubscribers: 2,
			wantError:       false,
		},
		{
			name:            "API error returns error",
			responseStatus:  http.StatusInternalServerError,
			responseBody:    map[string]interface{}{},
			wantSubscribers: 0,
			wantError:       true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				// Verify query parameters
				if status := r.URL.Query().Get("status"); status != "ACTIVE" {
					t.Errorf("expected status=ACTIVE, got %v", status)
				}

				w.WriteHeader(tt.responseStatus)
				json.NewEncoder(w).Encode(tt.responseBody)
			}))
			defer server.Close()

			client, err := NewClient(server.URL, "")
			if err != nil {
				t.Fatalf("NewClient() error = %v", err)
			}

			subscribers, err := client.FindActiveSubscribers(context.Background(), []string{"plan-1"})
			if (err != nil) != tt.wantError {
				t.Errorf("FindActiveSubscribers() error = %v, wantError %v", err, tt.wantError)
				return
			}
			if !tt.wantError && len(subscribers) != tt.wantSubscribers {
				t.Errorf("FindActiveSubscribers() returned %d subscribers, want %d", len(subscribers), tt.wantSubscribers)
			}
		})
	}
}

func TestFindActiveSubscribers_Pagination(t *testing.T) {
	pageCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		page := r.URL.Query().Get("page")
		pageCount++

		var response map[string]interface{}
		if page == "0" {
			response = map[string]interface{}{
				"content": []map[string]string{
					{"userId": "usr-1", "userEmail": "user1@example.com"},
				},
				"totalPages": 2,
				"pageNumber": 0,
			}
		} else {
			response = map[string]interface{}{
				"content": []map[string]string{
					{"userId": "usr-2", "userEmail": "user2@example.com"},
				},
				"totalPages": 2,
				"pageNumber": 1,
			}
		}

		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	client, _ := NewClient(server.URL, "")
	subscribers, err := client.FindActiveSubscribers(context.Background(), []string{"plan-1"})
	if err != nil {
		t.Fatalf("FindActiveSubscribers() error = %v", err)
	}

	if pageCount != 2 {
		t.Errorf("Expected 2 API calls for pagination, got %d", pageCount)
	}
	if len(subscribers) != 2 {
		t.Errorf("Expected 2 subscribers from 2 pages, got %d", len(subscribers))
	}
}

func TestFindActiveSubscribers_NilClient(t *testing.T) {
	var client *Client = nil
	_, err := client.FindActiveSubscribers(context.Background(), []string{"plan-1"})
	if err == nil {
		t.Error("FindActiveSubscribers() with nil client should return error")
	}
}
