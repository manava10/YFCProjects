CREATE TABLE portal_guard(id INTEGER PRIMARY KEY);
INSERT INTO portal_guard(id) VALUES (1);
CREATE TABLE account_applications (
 id UUID PRIMARY KEY,
 role VARCHAR(20) NOT NULL CHECK(role IN ('CUSTOMER','SUPPLIER','DRIVER')),
 username VARCHAR(80) NOT NULL UNIQUE,
 requested_username VARCHAR(80) NOT NULL,
 display_name VARCHAR(120) NOT NULL,
 mobile VARCHAR(20) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 gstin VARCHAR(15),
 address VARCHAR(500),
 email VARCHAR(254),
 license_number VARCHAR(40),
 license_expiry DATE,
 emergency_contact VARCHAR(160),
 emergency_mobile VARCHAR(20),
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 record_id UUID,
 account_id UUID REFERENCES user_accounts(id),
 branch_id UUID,
 decision_event_id UUID UNIQUE,
 decision_reason VARCHAR(1000),
 decided_by VARCHAR(100),
 decided_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 CHECK(status<>'APPROVED' OR (record_id IS NOT NULL AND account_id IS NOT NULL AND decision_event_id IS NOT NULL)),
 CHECK(status<>'REJECTED' OR (decision_reason IS NOT NULL AND decided_by IS NOT NULL AND decision_event_id IS NOT NULL)),
 CHECK(status<>'APPROVED' OR role<>'DRIVER' OR branch_id IS NOT NULL),
 CHECK(role<>'SUPPLIER' OR (gstin IS NOT NULL AND address IS NOT NULL)),
 CHECK(role<>'DRIVER' OR (license_number IS NOT NULL AND license_expiry IS NOT NULL AND emergency_contact IS NOT NULL AND emergency_mobile IS NOT NULL))
);
CREATE INDEX ix_account_applications_status ON account_applications(status,created_at,id);
CREATE TABLE account_ownership (
 account_id UUID PRIMARY KEY REFERENCES user_accounts(id),
 role VARCHAR(20) NOT NULL CHECK(role IN ('CUSTOMER','SUPPLIER','DRIVER')),
 record_id UUID NOT NULL,
 UNIQUE(role,record_id)
);
CREATE TABLE billing_tax_assessments (
 preparation_id UUID PRIMARY KEY REFERENCES billing_preparations(id),
 supply VARCHAR(12) NOT NULL CHECK(supply IN ('INTRA_STATE','INTER_STATE')),
 invoice_number VARCHAR(20) UNIQUE,
 taxable_amount NUMERIC(30,2) NOT NULL CHECK(taxable_amount>=0),
 cgst_amount NUMERIC(18,2) NOT NULL CHECK(cgst_amount>=0),
 sgst_amount NUMERIC(18,2) NOT NULL CHECK(sgst_amount>=0),
 igst_amount NUMERIC(18,2) NOT NULL CHECK(igst_amount>=0),
 tax_total NUMERIC(18,2) NOT NULL CHECK(tax_total>=0),
 payable_total NUMERIC(30,2) NOT NULL CHECK(payable_total>=taxable_amount),
 event_id UUID NOT NULL UNIQUE,
 assessed_by VARCHAR(100) NOT NULL,
 assessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 CHECK((supply='INTRA_STATE' AND igst_amount=0) OR (supply='INTER_STATE' AND cgst_amount=0 AND sgst_amount=0)),
 CHECK(tax_total=cgst_amount+sgst_amount+igst_amount),
 CHECK(payable_total=taxable_amount+tax_total)
);
CREATE TABLE billing_tax_lines (
 preparation_id UUID NOT NULL REFERENCES billing_tax_assessments(preparation_id),
 position INTEGER NOT NULL CHECK(position>0),
 component VARCHAR(10) NOT NULL CHECK(component IN ('MATERIAL','TRANSPORT')),
 hsn_or_sac VARCHAR(8) NOT NULL CHECK(hsn_or_sac=TRIM(hsn_or_sac)),
 rate_percent NUMERIC(5,2) NOT NULL CHECK(rate_percent>=0 AND rate_percent<=100),
 taxable_amount NUMERIC(30,2) NOT NULL CHECK(taxable_amount>0),
 cgst_amount NUMERIC(18,2) NOT NULL CHECK(cgst_amount>=0),
 sgst_amount NUMERIC(18,2) NOT NULL CHECK(sgst_amount>=0),
 igst_amount NUMERIC(18,2) NOT NULL CHECK(igst_amount>=0),
 PRIMARY KEY(preparation_id,position,component),
 CHECK(cgst_amount+sgst_amount+igst_amount>=0)
);
CREATE TABLE sales_invoice_counters (
 calendar_year INTEGER PRIMARY KEY,
 last_number INTEGER NOT NULL CHECK(last_number>=0)
);
