CREATE TABLE user_accounts (
 id UUID PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, username VARCHAR(80) NOT NULL UNIQUE,
 display_name VARCHAR(120) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(30) NOT NULL CHECK(role IN ('SUPER_ADMIN','OPERATIONS','ACCOUNTS','CUSTOMER','SUPPLIER','DRIVER')),
 enabled BOOLEAN NOT NULL DEFAULT TRUE,auth_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE identity_guard(id INTEGER PRIMARY KEY);
INSERT INTO identity_guard(id) VALUES (1);
CREATE TABLE password_resets (
 id UUID PRIMARY KEY,account_id UUID NOT NULL REFERENCES user_accounts(id),
 token_hash VARCHAR(64) NOT NULL UNIQUE,expires_at TIMESTAMP WITH TIME ZONE NOT NULL,used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_password_resets_account ON password_resets(account_id);
CREATE TABLE audit_events (
 id UUID PRIMARY KEY,occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 actor VARCHAR(80) NOT NULL,action VARCHAR(60) NOT NULL,target_type VARCHAR(40) NOT NULL,
 target_id UUID,detail VARCHAR(500) NOT NULL
);
CREATE INDEX ix_audit_events_occurred ON audit_events(occurred_at);
