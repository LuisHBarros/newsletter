ALTER TABLE billing_subscriptions ADD COLUMN billing_interval VARCHAR(20);
UPDATE billing_subscriptions SET billing_interval = 'MONTHLY' WHERE billing_interval IS NULL;
ALTER TABLE billing_subscriptions ALTER COLUMN billing_interval SET NOT NULL;