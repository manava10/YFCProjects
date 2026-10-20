CREATE TABLE stock_adjustments (
 id UUID PRIMARY KEY, kind VARCHAR(15) NOT NULL CHECK(kind IN ('CUSTOMER_RETURN','INCREASE','DECREASE')),
 yard_id UUID NOT NULL REFERENCES yards(id), material_id UUID NOT NULL REFERENCES materials(id),
 trip_id UUID REFERENCES trips(id), quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 expected_on_hand NUMERIC(19,3) CHECK(expected_on_hand>=0), occurred_on DATE NOT NULL,
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((kind='CUSTOMER_RETURN' AND trip_id IS NOT NULL AND expected_on_hand IS NULL) OR
       (kind IN ('INCREASE','DECREASE') AND trip_id IS NULL AND expected_on_hand IS NOT NULL)),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_adjustments_trip ON stock_adjustments(trip_id,status);
CREATE INDEX ix_adjustments_yard ON stock_adjustments(yard_id,material_id,submitted_at,id);
CREATE TABLE stock_adjustment_events (
 id UUID PRIMARY KEY, adjustment_id UUID NOT NULL REFERENCES stock_adjustments(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE stock_adjustment_movements (
 id UUID PRIMARY KEY REFERENCES stock_adjustment_events(id), adjustment_id UUID NOT NULL UNIQUE REFERENCES stock_adjustments(id),
 yard_id UUID NOT NULL, material_id UUID NOT NULL, kind VARCHAR(10) NOT NULL CHECK(kind IN ('RETURN','ADJUST_IN','ADJUST_OUT')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), reference VARCHAR(300) NOT NULL,
 recorded_by VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(yard_id,material_id) REFERENCES stock_balances(yard_id,material_id)
);
