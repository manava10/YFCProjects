ALTER TABLE yards ADD COLUMN kind VARCHAR(12) DEFAULT 'OPERATING' NOT NULL;
ALTER TABLE yards ADD CONSTRAINT yards_kind_check CHECK (kind IN ('OPERATING','QUARANTINE'));

ALTER TABLE private_documents ADD COLUMN target_type_v VARCHAR(24);
UPDATE private_documents SET target_type_v = target_type;
ALTER TABLE private_documents DROP COLUMN target_type;
ALTER TABLE private_documents RENAME COLUMN target_type_v TO target_type;
ALTER TABLE private_documents ALTER COLUMN target_type SET NOT NULL;
ALTER TABLE private_documents ADD CONSTRAINT private_documents_target_type_check
 CHECK (target_type IN ('TRIP','INCIDENT','FUEL','EXPENSE','STOCK_ADJUSTMENT','STOCK_TRANSFER','STOCK_OPENING'));
CREATE INDEX ix_private_documents_target ON private_documents(target_type,target_id,uploaded_at,id);

CREATE TABLE odometer_resets (
 id UUID PRIMARY KEY, truck_id UUID NOT NULL REFERENCES trucks(id),
 new_reading NUMERIC(12,1) NOT NULL CHECK(new_reading>=0),
 reset_at TIMESTAMP WITH TIME ZONE NOT NULL,
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_odometer_resets_truck ON odometer_resets(truck_id,reset_at,id);
CREATE TABLE odometer_reset_claims (
 truck_id UUID PRIMARY KEY REFERENCES trucks(id),
 reset_id UUID NOT NULL UNIQUE REFERENCES odometer_resets(id)
);
CREATE TABLE odometer_reset_events (
 id UUID PRIMARY KEY, reset_id UUID NOT NULL REFERENCES odometer_resets(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fuel_allocations (
 id UUID PRIMARY KEY, fuel_id UUID NOT NULL REFERENCES fuel_entries(id),
 trip_id UUID NOT NULL REFERENCES trips(id),
 quantity_litres NUMERIC(14,3) NOT NULL CHECK(quantity_litres>0),
 amount NUMERIC(26,2) NOT NULL CHECK(amount>0),
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 submitted_by VARCHAR(100) NOT NULL, submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 CHECK((status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND LENGTH(TRIM(review_reason))>0))
);
CREATE INDEX ix_fuel_allocations_fuel ON fuel_allocations(fuel_id,submitted_at,id);
CREATE INDEX ix_fuel_allocations_trip ON fuel_allocations(trip_id,status,id);
CREATE TABLE fuel_allocation_claims (
 fuel_id UUID NOT NULL REFERENCES fuel_entries(id),
 trip_id UUID NOT NULL REFERENCES trips(id),
 allocation_id UUID NOT NULL UNIQUE REFERENCES fuel_allocations(id),
 PRIMARY KEY(fuel_id,trip_id)
);
CREATE TABLE fuel_allocation_events (
 id UUID PRIMARY KEY, allocation_id UUID NOT NULL REFERENCES fuel_allocations(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('APPROVE','REJECT')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
