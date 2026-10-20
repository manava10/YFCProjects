CREATE TABLE trip_weight_corrections (
 id UUID PRIMARY KEY, trip_id UUID NOT NULL REFERENCES trips(id),
 loaded_gross NUMERIC(14,3) NOT NULL, loaded_tare NUMERIC(14,3) NOT NULL, loaded_quantity NUMERIC(14,3) NOT NULL,
 received_gross NUMERIC(14,3) NOT NULL, received_tare NUMERIC(14,3) NOT NULL, received_quantity NUMERIC(14,3) NOT NULL,
 accepted_quantity NUMERIC(14,3) NOT NULL, discrepancy_reason VARCHAR(1000),
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK(loaded_quantity>0 AND loaded_gross>loaded_tare AND loaded_quantity=loaded_gross-loaded_tare),
 CHECK(received_quantity>0 AND received_gross>received_tare AND received_quantity=received_gross-received_tare
       AND accepted_quantity>=0 AND accepted_quantity<=received_quantity AND received_quantity<=loaded_quantity),
 CHECK((loaded_quantity=received_quantity AND received_quantity=accepted_quantity)
       OR (discrepancy_reason IS NOT NULL AND LENGTH(TRIM(discrepancy_reason))>0)),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_trip_weight_corrections_trip ON trip_weight_corrections(trip_id,submitted_at,id);
CREATE TABLE trip_weight_correction_claims (
 trip_id UUID PRIMARY KEY REFERENCES trips(id),
 correction_id UUID NOT NULL UNIQUE REFERENCES trip_weight_corrections(id)
);
CREATE TABLE trip_weight_correction_events (
 id UUID PRIMARY KEY, correction_id UUID NOT NULL REFERENCES trip_weight_corrections(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_receipt_reversals (
 id UUID PRIMARY KEY, receipt_id UUID NOT NULL REFERENCES purchase_receipts(id),
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE TABLE purchase_receipt_reversal_claims (
 receipt_id UUID PRIMARY KEY REFERENCES purchase_receipts(id),
 reversal_id UUID NOT NULL UNIQUE REFERENCES purchase_receipt_reversals(id)
);
CREATE TABLE purchase_receipt_reversal_events (
 id UUID PRIMARY KEY, reversal_id UUID NOT NULL REFERENCES purchase_receipt_reversals(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE execution_stock_movements (
 id UUID PRIMARY KEY, source_type VARCHAR(20) NOT NULL CHECK(source_type IN ('WEIGHT','RECEIPT_REVERSAL')),
 source_id UUID NOT NULL, yard_id UUID NOT NULL, material_id UUID NOT NULL,
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('ADJUST_IN','ADJUST_OUT')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), reference VARCHAR(300) NOT NULL,
 recorded_by VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(yard_id,material_id) REFERENCES stock_balances(yard_id,material_id)
);
CREATE INDEX ix_execution_movements_stock ON execution_stock_movements(yard_id,material_id,created_at,id);
