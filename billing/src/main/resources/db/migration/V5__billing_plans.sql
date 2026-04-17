-- Create billing_plans table for plan catalog synchronization
CREATE TABLE billing_plans (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    trial_days INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_billing_plans_plan_id ON billing_plans(plan_id);
