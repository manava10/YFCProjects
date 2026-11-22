-- Multi-state branch GSTIN, state code, and settlement bank details
ALTER TABLE branches ADD COLUMN gstin VARCHAR(15);
ALTER TABLE branches ADD COLUMN state_code VARCHAR(2);
ALTER TABLE branches ADD COLUMN bank_name VARCHAR(160);
ALTER TABLE branches ADD COLUMN bank_account VARCHAR(40);
ALTER TABLE branches ADD COLUMN bank_ifsc VARCHAR(15);

-- Per-GST-state + Financial Year sales invoice sequence (CGST Rule 46 compliant <= 16 chars)
CREATE TABLE state_invoice_counters (
  state_code VARCHAR(4) NOT NULL,
  fy_start INTEGER NOT NULL,
  last_number INTEGER NOT NULL CHECK(last_number >= 0),
  PRIMARY KEY (state_code, fy_start)
);

-- E-Way Bill tracking on Trips (critical for inter-state goods movement > Rs 50,000)
ALTER TABLE trips ADD COLUMN eway_bill_number VARCHAR(20);
ALTER TABLE trips ADD COLUMN eway_bill_valid_until TIMESTAMP WITH TIME ZONE;
CREATE INDEX ix_trips_eway_expiry ON trips(eway_bill_valid_until);
