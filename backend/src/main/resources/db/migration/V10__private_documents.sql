CREATE TABLE private_documents (
 id UUID PRIMARY KEY,
 target_type VARCHAR(10) NOT NULL CHECK(target_type IN ('TRIP','INCIDENT','FUEL','EXPENSE')),
 target_id UUID NOT NULL,
 filename VARCHAR(160) NOT NULL,
 media_type VARCHAR(30) NOT NULL CHECK(media_type IN ('application/pdf','image/jpeg','image/png')),
 byte_size INTEGER NOT NULL CHECK(byte_size BETWEEN 1 AND 2097152),
 sha256 VARCHAR(64) NOT NULL,
 content BYTEA NOT NULL,
 uploaded_by VARCHAR(100) NOT NULL,
 uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(OCTET_LENGTH(content)=byte_size)
);
CREATE INDEX ix_private_documents_target ON private_documents(target_type,target_id,uploaded_at,id);
