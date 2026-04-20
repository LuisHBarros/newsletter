-- Add stripe_price_id column to plans table for Stripe Subscription integration
ALTER TABLE plans ADD COLUMN stripe_price_id VARCHAR(255);

COMMENT ON COLUMN plans.stripe_price_id IS 'Stripe Price ID (e.g., price_xxx) for subscription creation';
