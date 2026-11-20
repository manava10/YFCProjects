CREATE TABLE invoice_allocations (
 id UUID PRIMARY KEY,
 voucher_id UUID NOT NULL REFERENCES money_vouchers(id),
 preparation_id UUID NOT NULL REFERENCES billing_preparations(id),
 amount NUMERIC(18,2) NOT NULL CHECK(amount > 0),
 note VARCHAR(1000),
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REVERSED')),
 create_payload TEXT NOT NULL,
 created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reversal_event_id UUID UNIQUE,
 reversal_reason VARCHAR(1000),
 reversed_by VARCHAR(100),
 reversed_at TIMESTAMP WITH TIME ZONE,
 CHECK(status <> 'REVERSED' OR (reversal_event_id IS NOT NULL AND reversal_reason IS NOT NULL AND LENGTH(TRIM(reversal_reason)) > 0 AND reversed_by IS NOT NULL AND reversed_at IS NOT NULL))
);
CREATE INDEX ix_invoice_allocations_voucher ON invoice_allocations(voucher_id,status,created_at,id);
CREATE INDEX ix_invoice_allocations_preparation ON invoice_allocations(preparation_id,status,created_at,id);
