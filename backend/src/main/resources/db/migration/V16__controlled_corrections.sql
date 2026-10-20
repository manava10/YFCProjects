ALTER TABLE billing_preparations ADD COLUMN journal_id UUID UNIQUE REFERENCES journals(id);
CREATE TABLE billing_preparation_journals (
 journal_id UUID PRIMARY KEY REFERENCES journals(id),
 preparation_id UUID NOT NULL REFERENCES billing_preparations(id),
 counter_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
 create_payload TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_billing_journals_prep ON billing_preparation_journals(preparation_id,created_at,journal_id);

ALTER TABLE stock_adjustments ADD COLUMN resalable BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE stock_reversals (
 id UUID PRIMARY KEY, adjustment_id UUID NOT NULL REFERENCES stock_adjustments(id),
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE TABLE stock_reversal_claims (
 adjustment_id UUID PRIMARY KEY REFERENCES stock_adjustments(id),
 reversal_id UUID NOT NULL UNIQUE REFERENCES stock_reversals(id)
);
CREATE TABLE stock_reversal_events (
 id UUID PRIMARY KEY, reversal_id UUID NOT NULL REFERENCES stock_reversals(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_openings (
 id UUID PRIMARY KEY, yard_id UUID NOT NULL REFERENCES yards(id), material_id UUID NOT NULL REFERENCES materials(id),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), occurred_on DATE NOT NULL,
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE TABLE stock_opening_claims (
 yard_id UUID NOT NULL, material_id UUID NOT NULL,
 opening_id UUID NOT NULL UNIQUE REFERENCES stock_openings(id),
 PRIMARY KEY(yard_id,material_id)
);
CREATE TABLE stock_opening_events (
 id UUID PRIMARY KEY, opening_id UUID NOT NULL REFERENCES stock_openings(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_transfers (
 id UUID PRIMARY KEY, from_yard_id UUID NOT NULL REFERENCES yards(id), to_yard_id UUID NOT NULL REFERENCES yards(id),
 material_id UUID NOT NULL REFERENCES materials(id), quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 occurred_on DATE NOT NULL, reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK(from_yard_id<>to_yard_id),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_stock_transfers_yards ON stock_transfers(from_yard_id,to_yard_id,submitted_at,id);
CREATE TABLE stock_transfer_events (
 id UUID PRIMARY KEY, transfer_id UUID NOT NULL REFERENCES stock_transfers(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supplier_returns (
 id UUID PRIMARY KEY, purchase_id UUID NOT NULL REFERENCES purchase_orders(id),
 yard_id UUID NOT NULL REFERENCES yards(id), material_id UUID NOT NULL REFERENCES materials(id),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), occurred_on DATE NOT NULL,
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_supplier_returns_purchase ON supplier_returns(purchase_id,status);
CREATE TABLE supplier_return_events (
 id UUID PRIMARY KEY, return_id UUID NOT NULL REFERENCES supplier_returns(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_correction_movements (
 id UUID PRIMARY KEY, source_type VARCHAR(20) NOT NULL CHECK(source_type IN ('REVERSAL','OPENING','TRANSFER','SUPPLIER_RETURN')),
 source_id UUID NOT NULL, yard_id UUID NOT NULL, material_id UUID NOT NULL,
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('ADJUST_IN','ADJUST_OUT','TRANSFER_OUT','TRANSFER_IN','OPENING','SUPPLIER_RETURN')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), reference VARCHAR(300) NOT NULL,
 recorded_by VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(yard_id,material_id) REFERENCES stock_balances(yard_id,material_id)
);
CREATE INDEX ix_correction_movements_stock ON stock_correction_movements(yard_id,material_id,created_at,id);

CREATE TABLE operating_reversals (
 id UUID PRIMARY KEY, kind VARCHAR(10) NOT NULL CHECK(kind IN ('FUEL','EXPENSE')),
 source_id UUID NOT NULL, reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE TABLE operating_reversal_claims (
 kind VARCHAR(10) NOT NULL, source_id UUID NOT NULL,
 reversal_id UUID NOT NULL UNIQUE REFERENCES operating_reversals(id),
 PRIMARY KEY(kind,source_id)
);
CREATE TABLE operating_reversal_events (
 id UUID PRIMARY KEY, reversal_id UUID NOT NULL REFERENCES operating_reversals(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE login_failures (
 username VARCHAR(80) NOT NULL, failed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_login_failures ON login_failures(username,failed_at);
