-- Newsletter catalog
CREATE TABLE newsletters (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',            -- ACTIVE | ARCHIVED
    notion_database_id VARCHAR(128) NOT NULL UNIQUE,
    default_from_email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- newsletter <-> plan (source of truth for "which plan unlocks which newsletter")
CREATE TABLE newsletter_plans (
    newsletter_id UUID NOT NULL REFERENCES newsletters(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL,                                   -- FK logical to subscriptions.plan
    access_tier VARCHAR(16) NOT NULL DEFAULT 'FULL',         -- FULL | PREVIEW
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (newsletter_id, plan_id)
);

-- Issues (editions of a newsletter)
CREATE TABLE issues (
    id UUID PRIMARY KEY,
    newsletter_id UUID NOT NULL REFERENCES newsletters(id),
    notion_page_id VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(512) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    summary TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',             -- DRAFT | SCHEDULED | PUBLISHED | ARCHIVED
    scheduled_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    notion_last_edited_at TIMESTAMPTZ,
    html_s3_key VARCHAR(512),
    cover_image_s3_key VARCHAR(512),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_issue_slug_per_newsletter UNIQUE (newsletter_id, slug)
);

CREATE INDEX idx_issues_newsletter_status_scheduled ON issues(newsletter_id, status, scheduled_at);
CREATE INDEX idx_issues_status_scheduled ON issues(status, scheduled_at);

-- Assets (Notion images re-hosted in S3)
CREATE TABLE issue_assets (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    s3_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100),
    original_notion_url TEXT,
    checksum VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_issue_assets_issue_id ON issue_assets(issue_id);

-- updated_at trigger
CREATE OR REPLACE FUNCTION content_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_newsletters_updated_at BEFORE UPDATE ON newsletters
    FOR EACH ROW EXECUTE FUNCTION content_set_updated_at();
CREATE TRIGGER trg_issues_updated_at BEFORE UPDATE ON issues
    FOR EACH ROW EXECUTE FUNCTION content_set_updated_at();
