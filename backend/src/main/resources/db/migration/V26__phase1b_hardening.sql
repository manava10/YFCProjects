-- Phase 1B: Persistent DB-backed sessions, pluggable document object storage (DATABASE / LOCAL / S3), and search/filter indexes.

CREATE TABLE persistent_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  account_id UUID NOT NULL,
  username VARCHAR(80) NOT NULL,
  role VARCHAR(30) NOT NULL,
  auth_version BIGINT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_persistent_sessions_expires ON persistent_sessions(expires_at);
CREATE INDEX ix_persistent_sessions_account ON persistent_sessions(account_id);

-- Migrate private_documents to allow external object storage (LOCAL / S3) with zero database bloat
-- while keeping existing DATABASE rows intact and strictly checked.
CREATE TABLE private_documents_v26 (
  id UUID PRIMARY KEY,
  target_type VARCHAR(24) NOT NULL CHECK(target_type IN ('TRIP','INCIDENT','FUEL','EXPENSE','STOCK_ADJUSTMENT','STOCK_TRANSFER','STOCK_OPENING')),
  target_id UUID NOT NULL,
  filename VARCHAR(160) NOT NULL,
  media_type VARCHAR(30) NOT NULL CHECK(media_type IN ('application/pdf','image/jpeg','image/png')),
  byte_size INTEGER NOT NULL CHECK(byte_size BETWEEN 1 AND 2097152),
  sha256 VARCHAR(64) NOT NULL,
  storage_backend VARCHAR(20) NOT NULL DEFAULT 'DATABASE' CHECK(storage_backend IN ('DATABASE','LOCAL','S3')),
  storage_key VARCHAR(300),
  content BYTEA,
  uploaded_by VARCHAR(100) NOT NULL,
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT private_documents_payload_check CHECK(
    (storage_backend = 'DATABASE' AND content IS NOT NULL AND OCTET_LENGTH(content) = byte_size)
    OR (storage_backend IN ('LOCAL','S3') AND storage_key IS NOT NULL AND LENGTH(TRIM(storage_key)) > 0)
  )
);

INSERT INTO private_documents_v26(
  id, target_type, target_id, filename, media_type, byte_size, sha256,
  storage_backend, storage_key, content, uploaded_by, uploaded_at
)
SELECT
  id, target_type, target_id, filename, media_type, byte_size, sha256,
  'DATABASE', NULL, content, uploaded_by, uploaded_at
FROM private_documents;

DROP INDEX ix_private_documents_target;
DROP TABLE private_documents;
ALTER TABLE private_documents_v26 RENAME TO private_documents;
CREATE INDEX ix_private_documents_target ON private_documents(target_type, target_id, uploaded_at, id);

-- Server-side filter & search indexes for high-volume ERP lists
CREATE INDEX ix_sales_orders_branch_created ON sales_orders(branch_id, created_at);
CREATE INDEX ix_trips_status_planned ON trips(status, planned_date);
CREATE INDEX ix_purchase_orders_branch_status ON purchase_orders(branch_id, status, created_at);
CREATE INDEX ix_billing_preparations_status_created ON billing_preparations(status, created_at);
