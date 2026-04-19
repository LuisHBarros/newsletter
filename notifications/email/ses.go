package email

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/sesv2"
	"github.com/aws/aws-sdk-go-v2/service/sesv2/types"
)

type Client struct {
	client *sesv2.Client
	sender string
}

func NewClient(client *sesv2.Client, sender string) *Client {
	return &Client{client: client, sender: sender}
}

// SendTemplated sends an email using an SES template.
// SES failures bubble up to the SQS handler which marks the batch item failed.
// SQS redrive policy handles retries - no local backoff needed.
func (c *Client) SendTemplated(ctx context.Context, to string, templateName string, data map[string]string) error {
	templateData, err := toJSON(data)
	if err != nil {
		return fmt.Errorf("marshal template data: %w", err)
	}

	input := &sesv2.SendEmailInput{
		FromEmailAddress: aws.String(c.sender),
		Destination: &types.Destination{
			ToAddresses: []string{to},
		},
		Content: &types.EmailContent{
			Template: &types.Template{
				TemplateName: aws.String(templateName),
				TemplateData: aws.String(templateData),
			},
		},
	}

	_, err = c.client.SendEmail(ctx, input)
	if err != nil {
		return fmt.Errorf("ses send: %w", err)
	}
	return nil
}

// toJSON converts template data to JSON.
// SES template data must be a JSON object with string values.
// Numeric/boolean values are stringified before sending.
func toJSON(data map[string]string) (string, error) {
	buf, err := json.Marshal(data)
	if err != nil {
		return "", fmt.Errorf("marshal template data: %w", err)
	}
	return string(buf), nil
}
