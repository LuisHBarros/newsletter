package subscriptions

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"
)

// Subscriber represents an active subscription with user info.
type Subscriber struct {
	UserID    string `json:"userId"`
	UserEmail string `json:"userEmail"`
}

// Client queries the subscriptions service for active subscribers.
type Client struct {
	baseURL string
	apiKey  string
	client  *http.Client
}

// NewClient creates a subscriptions API client.
func NewClient(baseURL, apiKey string) (*Client, error) {
	if baseURL == "" {
		return nil, fmt.Errorf("baseURL cannot be empty")
	}
	return &Client{
		baseURL: baseURL,
		apiKey:  apiKey,
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}, nil
}

// paginatedResponse represents the paginated API response structure.
type paginatedResponse struct {
	Content []struct {
		UserID    string `json:"userId"`
		UserEmail string `json:"userEmail"`
	} `json:"content"`
	TotalPages int `json:"totalPages"`
	PageNumber int `json:"pageNumber"`
}

// FindActiveSubscribers returns active subscribers for the given plan IDs.
// Handles pagination to fetch all subscribers across multiple pages.
func (c *Client) FindActiveSubscribers(ctx context.Context, planIDs []string) ([]Subscriber, error) {
	if c == nil {
		return nil, fmt.Errorf("subscriptions client not configured")
	}

	var allSubscribers []Subscriber
	page := 0

	for {
		subscribers, totalPages, err := c.fetchSubscribersPage(ctx, planIDs, page)
		if err != nil {
			return nil, err
		}

		allSubscribers = append(allSubscribers, subscribers...)

		if page >= totalPages-1 {
			break
		}
		page++
	}

	return allSubscribers, nil
}

func (c *Client) fetchSubscribersPage(ctx context.Context, planIDs []string, page int) ([]Subscriber, int, error) {
	u, err := url.Parse(c.baseURL + "/subscriptions")
	if err != nil {
		return nil, 0, fmt.Errorf("parse url: %w", err)
	}

	q := u.Query()
	q.Set("status", "ACTIVE")
	q.Set("page", fmt.Sprintf("%d", page))
	for _, pid := range planIDs {
		q.Add("planIds", pid)
	}
	u.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, 0, fmt.Errorf("create request: %w", err)
	}

	if c.apiKey != "" {
		req.Header.Set("X-API-Key", c.apiKey)
	}

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("fetch subscribers: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, 0, fmt.Errorf("subscriptions API returned %d", resp.StatusCode)
	}

	var result paginatedResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, 0, fmt.Errorf("decode response: %w", err)
	}

	subscribers := make([]Subscriber, 0, len(result.Content))
	for _, s := range result.Content {
		if s.UserEmail != "" {
			subscribers = append(subscribers, Subscriber{
				UserID:    s.UserID,
				UserEmail: s.UserEmail,
			})
		}
	}

	return subscribers, result.TotalPages, nil
}
