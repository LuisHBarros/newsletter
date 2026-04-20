package idempotency

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

const ttlDays = 7

var ErrAlreadyProcessed = errors.New("event already processed")

type Store struct {
	client *dynamodb.Client
	table  string
}

func NewStore(client *dynamodb.Client, tableName string) *Store {
	return &Store{client: client, table: tableName}
}

func (s *Store) ClaimProcessing(ctx context.Context, eventID, handler string) error {
	_, err := s.client.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: aws.String(s.table),
		Item: map[string]types.AttributeValue{
			"eventId": &types.AttributeValueMemberS{Value: eventID},
			"handler": &types.AttributeValueMemberS{Value: handler},
			"ttl":     &types.AttributeValueMemberN{Value: fmt.Sprintf("%d", time.Now().Add(ttlDays*24*time.Hour).Unix())},
		},
		ConditionExpression: aws.String("attribute_not_exists(eventId)"),
	})
	if err != nil {
		var ccfe *types.ConditionalCheckFailedException
		if errors.As(err, &ccfe) {
			return ErrAlreadyProcessed
		}
		return fmt.Errorf("dynamodb put: %w", err)
	}
	return nil
}

func (s *Store) DeleteClaim(ctx context.Context, eventID, handler string) error {
	_, err := s.client.DeleteItem(ctx, &dynamodb.DeleteItemInput{
		TableName: aws.String(s.table),
		Key: map[string]types.AttributeValue{
			"eventId": &types.AttributeValueMemberS{Value: eventID},
			"handler": &types.AttributeValueMemberS{Value: handler},
		},
	})
	if err != nil {
		return fmt.Errorf("dynamodb delete: %w", err)
	}
	return nil
}
