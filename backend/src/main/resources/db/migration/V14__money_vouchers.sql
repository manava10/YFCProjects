CREATE TABLE treasury_accounts (
 account_id UUID PRIMARY KEY REFERENCES ledger_accounts(id), branch_id UUID NOT NULL REFERENCES branches(id),
 kind VARCHAR(4) NOT NULL CHECK(kind IN ('CASH','BANK')), reference VARCHAR(300) NOT NULL,
 create_payload TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE money_vouchers (
 id UUID PRIMARY KEY REFERENCES journals(id), profile_id UUID NOT NULL REFERENCES party_financial_profiles(id),
 money_account_id UUID NOT NULL REFERENCES treasury_accounts(account_id),
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','CUSTOMER_REFUND','SUPPLIER_REFUND')),
 amount NUMERIC(18,2) NOT NULL CHECK(amount>0), reference VARCHAR(300) NOT NULL,
 reference_key VARCHAR(300) NOT NULL, evidence_reference VARCHAR(500) NOT NULL,
 create_payload TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(LENGTH(TRIM(reference))>0 AND LENGTH(TRIM(evidence_reference))>0)
);
CREATE INDEX ix_money_vouchers_profile ON money_vouchers(profile_id,created_at,id);
CREATE TABLE voucher_reference_claims (
 money_account_id UUID NOT NULL REFERENCES treasury_accounts(account_id), reference_key VARCHAR(300) NOT NULL,
 voucher_id UUID NOT NULL UNIQUE REFERENCES money_vouchers(id), PRIMARY KEY(money_account_id,reference_key)
);
