CREATE TABLE billing_preparations (
 id UUID PRIMARY KEY,
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('SALES_ORDER','PURCHASE_RECEIPT')),
 source_id UUID NOT NULL, party_id UUID NOT NULL, branch_id UUID NOT NULL REFERENCES branches(id),
 party_name VARCHAR(160) NOT NULL, prepared_on DATE NOT NULL,
 quantity_basis VARCHAR(10) NOT NULL CHECK(quantity_basis IN ('ORDERED','LOADED','RECEIVED','ACCEPTED')),
 reference VARCHAR(300) NOT NULL CHECK(LENGTH(TRIM(reference))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 basis_reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(basis_reason))>0),
 source_snapshot TEXT NOT NULL, subtotal NUMERIC(30,2) NOT NULL CHECK(subtotal>=0),
 status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','READY','REJECTED','CANCELLED')),
 created_by VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL,
 CHECK((kind='SALES_ORDER' AND quantity_basis='ORDERED') OR
       (kind='PURCHASE_RECEIPT' AND quantity_basis IN ('LOADED','RECEIVED','ACCEPTED')))
);
CREATE INDEX ix_billing_preparations_list ON billing_preparations(created_at,id);
CREATE INDEX ix_billing_preparations_party ON billing_preparations(kind,party_id,prepared_on,id);
CREATE TABLE billing_preparation_lines (
 preparation_id UUID NOT NULL REFERENCES billing_preparations(id),
 position INTEGER NOT NULL CHECK(position>0), source_line_id UUID NOT NULL,
 material VARCHAR(160) NOT NULL, quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 loaded_quantity NUMERIC(19,3) NOT NULL CHECK(loaded_quantity>=0),
 received_quantity NUMERIC(19,3) NOT NULL CHECK(received_quantity>=0),
 accepted_quantity NUMERIC(14,3) CHECK(accepted_quantity>=0),
 material_rate NUMERIC(14,2) NOT NULL CHECK(material_rate>=0),
 transport_rate NUMERIC(14,2) NOT NULL CHECK(transport_rate>=0),
 material_amount NUMERIC(26,2) NOT NULL CHECK(material_amount>=0),
 transport_amount NUMERIC(26,2) NOT NULL CHECK(transport_amount>=0),
 PRIMARY KEY(preparation_id,position), UNIQUE(preparation_id,source_line_id)
);
CREATE TABLE billing_source_claims (
 kind VARCHAR(20) NOT NULL, source_id UUID NOT NULL,
 preparation_id UUID NOT NULL UNIQUE REFERENCES billing_preparations(id),
 PRIMARY KEY(kind,source_id)
);
CREATE TABLE billing_preparation_events (
 id UUID PRIMARY KEY, preparation_id UUID NOT NULL REFERENCES billing_preparations(id),
 action VARCHAR(10) NOT NULL CHECK(action IN ('READY','REJECT','CANCEL')),
 reason VARCHAR(1000) NOT NULL CHECK(LENGTH(TRIM(reason))>0), actor VARCHAR(100) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
