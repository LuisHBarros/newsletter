-- Records every Notion webhook received (dedupe + audit).
CREATE TABLE notion_webhook_deliveries (
    id UUID PRIMARY KEY,
    delivery_id VARCHAR(128) NOT NULL UNIQUE,
    signature_valid BOOLEAN NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',      -- RECEIVED | ACCEPTED | REJECTED | FAILED
    error TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_notion_webhook_deliveries_received_at ON notion_webhook_deliveries(received_at);
