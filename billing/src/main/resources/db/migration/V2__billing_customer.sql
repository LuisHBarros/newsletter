-- Maps a user to a payment-provider customer (e.g. Stripe customer id)
CREATE TABLE billing_customers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL DEFAULT 'FAKE', -- FAKE, STRIPE
    provider_customer_ref VARCHAR(255),
    email VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Maps a subscriptions-service subscription to a provider subscription
CREATE TABLE billing_subscriptions (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL UNIQUE, -- id from the subscriptions service
    customer_id UUID NOT NULL REFERENCES billing_customers(id),
    plan_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, ACTIVE, PAST_DUE, CANCELED
    provider_subscription_ref VARCHAR(255),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_billing_customers_user_id ON billing_customers(user_id);
CREATE INDEX idx_billing_subscriptions_subscription_id ON billing_subscriptions(subscription_id);
CREATE INDEX idx_billing_subscriptions_customer_id ON billing_subscriptions(customer_id);
CREATE INDEX idx_billing_subscriptions_status ON billing_subscriptions(status);

CREATE TRIGGER update_billing_customers_updated_at BEFORE UPDATE ON billing_customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_subscriptions_updated_at BEFORE UPDATE ON billing_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
