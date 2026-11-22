-- Phase 2: Preventive Fleet Maintenance, Scheduled Alert Digests, and GST Credit/Debit Notes (Section 34 CGST).

ALTER TABLE trucks ADD COLUMN current_odometer_km INTEGER NOT NULL DEFAULT 0;

CREATE TABLE fleet_maintenance_logs (
  id UUID PRIMARY KEY,
  truck_id UUID NOT NULL REFERENCES trucks(id),
  branch_id UUID REFERENCES branches(id),
  category VARCHAR(30) NOT NULL CHECK(category IN (
    'PREVENTIVE_SERVICE','TYRE','BRAKE_CLUTCH','ENGINE','BODY_HYDRAULIC','INSPECTION','OTHER'
  )),
  service_date DATE NOT NULL,
  odometer_km INTEGER NOT NULL CHECK(odometer_km >= 0),
  next_due_date DATE,
  next_due_odometer_km INTEGER CHECK(next_due_odometer_km IS NULL OR next_due_odometer_km >= odometer_km),
  vendor_name VARCHAR(160) NOT NULL,
  invoice_reference VARCHAR(120),
  cost_inr NUMERIC(14,2) NOT NULL CHECK(cost_inr >= 0),
  notes VARCHAR(1000),
  recorded_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_fleet_maint_truck_date ON fleet_maintenance_logs(truck_id, service_date);
CREATE INDEX ix_fleet_maint_due ON fleet_maintenance_logs(next_due_date);

CREATE TABLE alert_digests (
  id UUID PRIMARY KEY,
  digest_date DATE NOT NULL,
  critical_count INTEGER NOT NULL DEFAULT 0,
  warning_count INTEGER NOT NULL DEFAULT 0,
  total_alerts INTEGER NOT NULL DEFAULT 0,
  dispatched_channel VARCHAR(30) NOT NULL DEFAULT 'IN_APP',
  dispatch_status VARCHAR(30) NOT NULL DEFAULT 'RECORDED',
  summary_json TEXT NOT NULL,
  triggered_by VARCHAR(80) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_alert_digests_date ON alert_digests(digest_date, created_at);

CREATE TABLE state_note_counters (
  note_type VARCHAR(15) NOT NULL CHECK(note_type IN ('CREDIT_NOTE','DEBIT_NOTE')),
  state_code VARCHAR(4) NOT NULL,
  fy_start INTEGER NOT NULL,
  last_number INTEGER NOT NULL CHECK(last_number >= 0),
  PRIMARY KEY(note_type, state_code, fy_start)
);

CREATE TABLE gst_adjustment_notes (
  id UUID PRIMARY KEY,
  note_type VARCHAR(15) NOT NULL CHECK(note_type IN ('CREDIT_NOTE','DEBIT_NOTE')),
  note_number VARCHAR(20) NOT NULL UNIQUE,
  original_preparation_id UUID NOT NULL REFERENCES billing_preparations(id),
  original_invoice_number VARCHAR(30),
  branch_id UUID NOT NULL REFERENCES branches(id),
  party_id UUID NOT NULL,
  party_name VARCHAR(160) NOT NULL,
  kind VARCHAR(20) NOT NULL CHECK(kind IN ('SALES_ORDER','PURCHASE_RECEIPT')),
  note_date DATE NOT NULL,
  reason_code VARCHAR(30) NOT NULL CHECK(reason_code IN (
    'SALES_RETURN','RATE_DIFFERENCE','WEIGHT_SHORTAGE','QUALITY_DEDUCTION','POST_SALE_DISCOUNT','ADDITIONAL_CHARGE'
  )),
  reason_detail VARCHAR(1000) NOT NULL,
  supply VARCHAR(20) NOT NULL CHECK(supply IN ('INTRA_STATE','INTER_STATE')),
  hsn_or_sac VARCHAR(12) NOT NULL,
  taxable_amount NUMERIC(16,2) NOT NULL CHECK(taxable_amount > 0),
  gst_rate_percent NUMERIC(5,2) NOT NULL CHECK(gst_rate_percent >= 0 AND gst_rate_percent <= 100),
  cgst_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
  sgst_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
  igst_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
  tax_total NUMERIC(16,2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(16,2) NOT NULL CHECK(total_amount > 0),
  status VARCHAR(15) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','APPROVED','CANCELLED')),
  created_by VARCHAR(80) NOT NULL,
  approved_by VARCHAR(80),
  decision_reason VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_gst_notes_original ON gst_adjustment_notes(original_preparation_id);
CREATE INDEX ix_gst_notes_branch_date ON gst_adjustment_notes(branch_id, note_date);
