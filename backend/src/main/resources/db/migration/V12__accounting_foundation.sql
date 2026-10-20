CREATE TABLE accounting_guard (id INTEGER PRIMARY KEY CHECK(id=1), next_number BIGINT NOT NULL CHECK(next_number>0));
INSERT INTO accounting_guard(id,next_number) VALUES(1,1);
CREATE TABLE ledger_accounts (
 id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
 type VARCHAR(10) NOT NULL CHECK(type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
 active BOOLEAN NOT NULL DEFAULT TRUE, create_payload TEXT NOT NULL,
 created_by VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(code=UPPER(code)), CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE financial_periods (
 id UUID PRIMARY KEY, code VARCHAR(30) NOT NULL UNIQUE, starts_on DATE NOT NULL, ends_on DATE NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','CLOSED')),
 create_payload TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 closed_by VARCHAR(100), closed_at TIMESTAMP WITH TIME ZONE, close_reason VARCHAR(1000),
 CHECK(starts_on<=ends_on), CHECK(code=UPPER(code)),
 CHECK((status='OPEN' AND closed_by IS NULL AND closed_at IS NULL AND close_reason IS NULL) OR
       (status='CLOSED' AND closed_by IS NOT NULL AND closed_at IS NOT NULL AND close_reason IS NOT NULL AND LENGTH(TRIM(close_reason))>0))
);
CREATE TABLE journals (
 id UUID PRIMARY KEY, period_id UUID NOT NULL REFERENCES financial_periods(id), branch_id UUID NOT NULL REFERENCES branches(id),
 entry_date DATE NOT NULL, description VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(description))>0),
 reference VARCHAR(300) NOT NULL CHECK(LENGTH(TRIM(reference))>0), currency VARCHAR(3) NOT NULL DEFAULT 'INR' CHECK(currency='INR'),
 total_debit NUMERIC(20,2) NOT NULL CHECK(total_debit>0), total_credit NUMERIC(20,2) NOT NULL CHECK(total_credit=total_debit),
 status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','POSTED','REJECTED')),
 number VARCHAR(30) UNIQUE, submitted_by VARCHAR(100) NOT NULL,
 submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by VARCHAR(100), reviewed_at TIMESTAMP WITH TIME ZONE, review_reason VARCHAR(1000),
 reverses_id UUID REFERENCES journals(id), reversal_claim UUID UNIQUE REFERENCES journals(id), create_payload TEXT NOT NULL,
 CHECK(reverses_id IS NULL OR reverses_id<>id),
 CHECK((reverses_id IS NULL AND reversal_claim IS NULL) OR
       (reverses_id IS NOT NULL AND ((status='REJECTED' AND reversal_claim IS NULL) OR (status<>'REJECTED' AND reversal_claim=reverses_id AND reversal_claim IS NOT NULL)))),
 CHECK((status='DRAFT' AND number IS NULL AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR
       (status IN ('POSTED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_by<>submitted_by AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL AND LENGTH(TRIM(review_reason))>0 AND ((status='POSTED' AND number IS NOT NULL) OR (status='REJECTED' AND number IS NULL))))
);
CREATE TABLE journal_lines (
 journal_id UUID NOT NULL REFERENCES journals(id), position INTEGER NOT NULL CHECK(position BETWEEN 1 AND 100),
 account_id UUID NOT NULL REFERENCES ledger_accounts(id), debit NUMERIC(18,2) NOT NULL DEFAULT 0,
 credit NUMERIC(18,2) NOT NULL DEFAULT 0, narration VARCHAR(500) NOT NULL,
 PRIMARY KEY(journal_id,position), CHECK((debit>0 AND credit=0) OR (credit>0 AND debit=0))
);
CREATE INDEX ix_journals_date_branch ON journals(status,entry_date,branch_id,id);
CREATE INDEX ix_journals_period ON journals(period_id,status);
CREATE INDEX ix_journal_lines_account ON journal_lines(account_id,journal_id);
CREATE TABLE accounting_events (
 id UUID PRIMARY KEY, target_id UUID NOT NULL, target_type VARCHAR(10) NOT NULL CHECK(target_type IN ('JOURNAL','PERIOD')),
 action VARCHAR(10) NOT NULL CHECK(action IN ('POST','REJECT','CLOSE')), reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE ledger_account_status_events (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES ledger_accounts(id),
 old_active BOOLEAN NOT NULL, new_active BOOLEAN NOT NULL, reason VARCHAR(1000) NOT NULL,
 actor VARCHAR(100) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(old_active<>new_active)
);
