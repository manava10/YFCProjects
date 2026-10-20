CREATE TABLE party_financial_profiles (
 id UUID PRIMARY KEY, party_type VARCHAR(10) NOT NULL CHECK(party_type IN ('CUSTOMER','SUPPLIER')),
 customer_id UUID UNIQUE REFERENCES customers(id), supplier_id UUID UNIQUE REFERENCES suppliers(id),
 account_id UUID NOT NULL UNIQUE REFERENCES ledger_accounts(id), party_name VARCHAR(160) NOT NULL,
 payment_terms_days INTEGER NOT NULL CHECK(payment_terms_days BETWEEN 0 AND 365),
 credit_limit NUMERIC(18,2) CHECK(credit_limit>=0), version INTEGER NOT NULL DEFAULT 1 CHECK(version>0),
 create_payload TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((party_type='CUSTOMER' AND customer_id IS NOT NULL AND supplier_id IS NULL) OR
       (party_type='SUPPLIER' AND supplier_id IS NOT NULL AND customer_id IS NULL AND credit_limit IS NULL))
);
CREATE TABLE party_financial_events (
 id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES party_financial_profiles(id),
 from_version INTEGER NOT NULL, to_version INTEGER NOT NULL CHECK(to_version=from_version+1),
 previous_terms_days INTEGER NOT NULL, new_terms_days INTEGER NOT NULL,
 previous_credit_limit NUMERIC(18,2), new_credit_limit NUMERIC(18,2),
 reason VARCHAR(1000) NOT NULL, actor VARCHAR(100) NOT NULL, payload TEXT NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(profile_id,to_version)
);
CREATE INDEX ix_party_financial_events ON party_financial_events(profile_id,to_version);
