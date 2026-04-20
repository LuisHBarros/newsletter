package permissions

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type Permission struct {
	UserID    string     `dynamodbav:"userId" json:"userId"`
	ContentID string     `dynamodbav:"contentId" json:"contentId"`
	PlanID    string     `dynamodbav:"planId" json:"planId"`
	GrantedAt time.Time  `dynamodbav:"grantedAt" json:"grantedAt"`
	ExpiresAt *time.Time `dynamodbav:"expiresAt,omitempty" json:"expiresAt,omitempty"`
	TTL       int64      `dynamodbav:"ttl,omitempty" json:"-"`
}

type Store struct {
	client    *dynamodb.Client
	tableName string
}

func NewStore(client *dynamodb.Client, tableName string) *Store {
	return &Store{
		client:    client,
		tableName: tableName,
	}
}

func (s *Store) Grant(ctx context.Context, perm *Permission) error {
	now := time.Now().UTC()
	perm.GrantedAt = now

	if perm.ExpiresAt != nil {
		perm.TTL = perm.ExpiresAt.Unix()
	} else {
		perm.TTL = 0
	}

	item, err := attributevalue.MarshalMap(perm)
	if err != nil {
		return fmt.Errorf("marshal permission: %w", err)
	}

	_, err = s.client.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: aws.String(s.tableName),
		Item:      item,
	})
	if err != nil {
		return fmt.Errorf("put item: %w", err)
	}

	return nil
}

func (s *Store) Revoke(ctx context.Context, userID, contentID string) (bool, error) {
	result, err := s.client.DeleteItem(ctx, &dynamodb.DeleteItemInput{
		TableName: aws.String(s.tableName),
		Key: map[string]types.AttributeValue{
			"userId":    &types.AttributeValueMemberS{Value: userID},
			"contentId": &types.AttributeValueMemberS{Value: contentID},
		},
		ReturnValues: types.ReturnValueAllOld,
	})
	if err != nil {
		return false, fmt.Errorf("delete item: %w", err)
	}

	return result.Attributes != nil, nil
}

func (s *Store) Check(ctx context.Context, userID, contentID string) (*Permission, error) {
	result, err := s.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(s.tableName),
		Key: map[string]types.AttributeValue{
			"userId":    &types.AttributeValueMemberS{Value: userID},
			"contentId": &types.AttributeValueMemberS{Value: contentID},
		},
	})
	if err != nil {
		return nil, fmt.Errorf("get item: %w", err)
	}

	if result.Item == nil {
		return nil, nil
	}

	var perm Permission
	if err := attributevalue.UnmarshalMap(result.Item, &perm); err != nil {
		return nil, fmt.Errorf("unmarshal permission: %w", err)
	}

	if perm.ExpiresAt != nil && time.Now().UTC().After(*perm.ExpiresAt) {
		return nil, nil
	}

	return &perm, nil
}

func (s *Store) ListByUser(ctx context.Context, userID string) ([]Permission, error) {
	var allPerms []Permission
	var lastKey map[string]types.AttributeValue

	for {
		result, err := s.client.Query(ctx, &dynamodb.QueryInput{
			TableName:              aws.String(s.tableName),
			KeyConditionExpression: aws.String("userId = :userId"),
			ExpressionAttributeValues: map[string]types.AttributeValue{
				":userId": &types.AttributeValueMemberS{Value: userID},
			},
			ExclusiveStartKey: lastKey,
		})
		if err != nil {
			return nil, fmt.Errorf("query: %w", err)
		}

		var batch []Permission
		if err := attributevalue.UnmarshalListOfMaps(result.Items, &batch); err != nil {
			return nil, fmt.Errorf("unmarshal results: %w", err)
		}
		allPerms = append(allPerms, batch...)

		if result.LastEvaluatedKey == nil {
			break
		}
		lastKey = result.LastEvaluatedKey
	}

	var active []Permission
	now := time.Now().UTC()
	for _, p := range allPerms {
		if p.ExpiresAt == nil || now.Before(*p.ExpiresAt) {
			active = append(active, p)
		}
	}

	return active, nil
}
