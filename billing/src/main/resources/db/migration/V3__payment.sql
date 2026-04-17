-- Payments: ported from payment-service `transfers` table, adapted to billing domain.
-- Instead of source/destination wallets, a payment belongs to a BillingCustomer and a BillingSubscription.
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES billing_customers(id),
    subscription_id UUID REFERENCES billing_subscriptions(id),
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    status VARCHAR(50) NOT NULL, -- PENDING, SUCCEEDED, FAILED, REFUNDED, CANCELED
    description TEXT,
    provider VARCHAR(50) NOT NULL DEFAULT 'FAKE',
    provider_payment_ref VARCHAR(255),
    failure_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
