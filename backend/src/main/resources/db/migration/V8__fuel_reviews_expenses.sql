ALTER TABLE trips ADD CONSTRAINT uq_trip_truck UNIQUE(id,truck_id);
ALTER TABLE fuel_entries ALTER COLUMN amount TYPE NUMERIC(26,2);
ALTER TABLE fuel_entries ADD COLUMN trip_id UUID;
ALTER TABLE fuel_entries ADD COLUMN odometer NUMERIC(13,1);
ALTER TABLE fuel_entries ADD COLUMN filled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE fuel_entries ADD COLUMN receipt_evidence VARCHAR(300);
ALTER TABLE fuel_entries ADD COLUMN price_variance_reason VARCHAR(1000);
ALTER TABLE fuel_entries ADD COLUMN reference_price_per_litre NUMERIC(10,2);
UPDATE fuel_entries SET reference_price_per_litre=(SELECT price_per_litre FROM diesel_rates WHERE diesel_rates.id=fuel_entries.rate_id);
ALTER TABLE fuel_entries ALTER COLUMN reference_price_per_litre SET NOT NULL;
ALTER TABLE fuel_entries ADD COLUMN submitted_by VARCHAR(80) NOT NULL DEFAULT 'LEGACY_IMPORT';
ALTER TABLE fuel_entries ADD COLUMN status VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK(status IN ('PENDING_VERIFICATION','APPROVED','REJECTED'));
ALTER TABLE fuel_entries ADD COLUMN revision INTEGER NOT NULL DEFAULT 1 CHECK(revision>0);
ALTER TABLE fuel_entries ADD COLUMN reviewed_by VARCHAR(80);
ALTER TABLE fuel_entries ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE fuel_entries ADD COLUMN decision_reason VARCHAR(1000);
ALTER TABLE fuel_entries ADD COLUMN create_payload TEXT;
ALTER TABLE fuel_entries ADD CONSTRAINT fk_fuel_trip_truck FOREIGN KEY(trip_id,truck_id) REFERENCES trips(id,truck_id);
ALTER TABLE fuel_entries ADD CONSTRAINT ck_fuel_amount CHECK(amount=ROUND(quantity_litres*actual_price_per_litre,2));
ALTER TABLE fuel_entries ADD CONSTRAINT ck_fuel_reference_price CHECK(reference_price_per_litre>0);
ALTER TABLE fuel_entries ADD CONSTRAINT ck_fuel_odometer CHECK(odometer IS NULL OR odometer>=0);
ALTER TABLE fuel_entries ADD CONSTRAINT ck_fuel_approved CHECK(status<>'APPROVED' OR (odometer IS NOT NULL AND filled_at IS NOT NULL AND receipt_evidence IS NOT NULL AND LENGTH(TRIM(receipt_evidence))>0));
ALTER TABLE fuel_entries ADD CONSTRAINT ck_fuel_review CHECK(status='PENDING_VERIFICATION' OR (reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND decision_reason IS NOT NULL AND LENGTH(TRIM(decision_reason))>0));
CREATE INDEX ix_fuel_trip ON fuel_entries(trip_id,status);
CREATE INDEX ix_fuel_odometer ON fuel_entries(truck_id,status,filled_at);
CREATE TABLE operating_expenses (
 id UUID PRIMARY KEY, truck_id UUID NOT NULL REFERENCES trucks(id),trip_id UUID,
 category VARCHAR(30) NOT NULL CHECK(category IN ('TOLL','DRIVER_ALLOWANCE','PARKING','TYRE','MAINTENANCE','REPAIR','OTHER')),
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, amount NUMERIC(26,2) NOT NULL CHECK(amount>0),
 description VARCHAR(1000) NOT NULL, receipt_reference VARCHAR(100) NOT NULL UNIQUE, receipt_evidence VARCHAR(300),
 submitted_by VARCHAR(80) NOT NULL,status VARCHAR(25) NOT NULL DEFAULT 'PENDING_VERIFICATION' CHECK(status IN ('PENDING_VERIFICATION','APPROVED','REJECTED')),
 revision INTEGER NOT NULL DEFAULT 1 CHECK(revision>0),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP WITH TIME ZONE,decision_reason VARCHAR(1000),
 create_payload TEXT NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(trip_id,truck_id) REFERENCES trips(id,truck_id),
 CHECK(category NOT IN ('TOLL','DRIVER_ALLOWANCE','PARKING') OR trip_id IS NOT NULL),
 CHECK(status<>'APPROVED' OR (receipt_evidence IS NOT NULL AND LENGTH(TRIM(receipt_evidence))>0)),
 CHECK(status='PENDING_VERIFICATION' OR (reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND decision_reason IS NOT NULL AND LENGTH(TRIM(decision_reason))>0))
);
CREATE INDEX ix_expense_trip ON operating_expenses(trip_id,status);
CREATE TABLE operating_events (
 id UUID PRIMARY KEY,kind VARCHAR(10) NOT NULL CHECK(kind IN ('FUEL','EXPENSE')),record_id UUID NOT NULL,
 action VARCHAR(15) NOT NULL CHECK(action IN ('REVISE','APPROVE','REJECT')),revision INTEGER NOT NULL,
 payload TEXT NOT NULL,previous_snapshot TEXT,actor VARCHAR(80) NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_operating_events ON operating_events(kind,record_id,created_at);
CREATE TABLE diesel_rate_corrections (
 id UUID PRIMARY KEY,rate_id UUID NOT NULL REFERENCES diesel_rates(id),old_price NUMERIC(10,2) NOT NULL CHECK(old_price>0),
 new_price NUMERIC(10,2) NOT NULL CHECK(new_price>0),reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0),
 actor VARCHAR(80) NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_diesel_corrections ON diesel_rate_corrections(rate_id,created_at);
