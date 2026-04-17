-- Single Postgres instance, one database per service (mirrors prod RDS layout).
-- Each service connects with its own user/secret and cannot reach the others.

CREATE DATABASE newsletter_subscriptions;
CREATE DATABASE newsletter_billing;
CREATE DATABASE newsletter_content;

-- Dev credentials (prod uses Secrets Manager).
CREATE USER subscriptions_user WITH PASSWORD 'subscriptions_pwd';
CREATE USER billing_user       WITH PASSWORD 'billing_pwd';
CREATE USER content_user       WITH PASSWORD 'content_pwd';

GRANT ALL PRIVILEGES ON DATABASE newsletter_subscriptions TO subscriptions_user;
GRANT ALL PRIVILEGES ON DATABASE newsletter_billing       TO billing_user;
GRANT ALL PRIVILEGES ON DATABASE newsletter_content       TO content_user;
