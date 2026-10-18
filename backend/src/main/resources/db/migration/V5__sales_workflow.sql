CREATE TABLE rfqs (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,customer_id UUID NOT NULL REFERENCES customers(id),branch_id UUID NOT NULL REFERENCES branches(id),
 project_id UUID NOT NULL,delivery_site_id UUID NOT NULL,delivery_address VARCHAR(500) NOT NULL,business_type VARCHAR(30) NOT NULL CHECK(business_type IN ('MATERIAL','TRANSPORT','MATERIAL_AND_TRANSPORT')),
 required_date DATE NOT NULL,remarks VARCHAR(2000),status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','QUOTED','ACCEPTED','CONVERTED')),created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(id,customer_id,branch_id,project_id,delivery_site_id),
 FOREIGN KEY(project_id,customer_id,branch_id) REFERENCES customer_projects(id,customer_id,branch_id),
 FOREIGN KEY(delivery_site_id,project_id) REFERENCES delivery_sites(id,project_id)
);
CREATE TABLE rfq_items (
 id UUID PRIMARY KEY,rfq_id UUID NOT NULL REFERENCES rfqs(id),position INTEGER NOT NULL CHECK(position>0),material_id UUID NOT NULL REFERENCES materials(id),material VARCHAR(160) NOT NULL,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),UNIQUE(rfq_id,position),UNIQUE(rfq_id,material_id),UNIQUE(id,rfq_id)
);
CREATE TABLE quotations (
 id UUID PRIMARY KEY,rfq_id UUID NOT NULL REFERENCES rfqs(id),revision INTEGER NOT NULL CHECK(revision>0),status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','SENT','ACCEPTED','REJECTED','SUPERSEDED')),
 valid_until DATE NOT NULL,payment_terms VARCHAR(1000) NOT NULL,delivery_terms VARCHAR(1000) NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 sent_at TIMESTAMP WITH TIME ZONE,decided_at TIMESTAMP WITH TIME ZONE,decided_by VARCHAR(80),customer_contact VARCHAR(160),approval_reference VARCHAR(300),decision_note VARCHAR(1000),
 UNIQUE(rfq_id,revision),UNIQUE(id,rfq_id)
);
CREATE TABLE quotation_items (
 id UUID PRIMARY KEY,quotation_id UUID NOT NULL,rfq_id UUID NOT NULL,rfq_item_id UUID NOT NULL,position INTEGER NOT NULL CHECK(position>0),
 material_id UUID NOT NULL REFERENCES materials(id),material VARCHAR(160) NOT NULL,quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 material_rate NUMERIC(14,2) NOT NULL CHECK(material_rate>=0),transport_rate NUMERIC(14,2) NOT NULL CHECK(transport_rate>=0),
 material_amount NUMERIC(26,2) NOT NULL CHECK(material_amount>=0),transport_amount NUMERIC(26,2) NOT NULL CHECK(transport_amount>=0),
 FOREIGN KEY(quotation_id,rfq_id) REFERENCES quotations(id,rfq_id),FOREIGN KEY(rfq_item_id,rfq_id) REFERENCES rfq_items(id,rfq_id),
 UNIQUE(quotation_id,position),UNIQUE(quotation_id,rfq_item_id)
);
ALTER TABLE sales_orders ADD COLUMN source_rfq_id UUID UNIQUE REFERENCES rfqs(id);
ALTER TABLE sales_orders ADD COLUMN source_quotation_id UUID UNIQUE;
ALTER TABLE sales_orders ADD COLUMN required_date DATE;
ALTER TABLE sales_orders ADD COLUMN payment_terms VARCHAR(1000);
ALTER TABLE sales_orders ADD COLUMN delivery_terms VARCHAR(1000);
ALTER TABLE sales_orders ADD CONSTRAINT fk_order_source_quote FOREIGN KEY(source_quotation_id,source_rfq_id) REFERENCES quotations(id,rfq_id);
ALTER TABLE sales_orders ADD CONSTRAINT fk_order_source_rfq_customer FOREIGN KEY(source_rfq_id,customer_id,branch_id,project_id,delivery_site_id) REFERENCES rfqs(id,customer_id,branch_id,project_id,delivery_site_id);
ALTER TABLE sales_orders ADD CONSTRAINT ck_order_source_pair CHECK((source_rfq_id IS NULL AND source_quotation_id IS NULL) OR (source_rfq_id IS NOT NULL AND source_quotation_id IS NOT NULL));
CREATE TABLE sales_order_items (
 id UUID PRIMARY KEY,order_id UUID NOT NULL REFERENCES sales_orders(id),source_rfq_item_id UUID REFERENCES rfq_items(id),material_id UUID REFERENCES materials(id),position INTEGER NOT NULL CHECK(position>0),material VARCHAR(160) NOT NULL,
 ordered_quantity NUMERIC(14,3) NOT NULL CHECK(ordered_quantity>0),material_rate NUMERIC(14,2) NOT NULL CHECK(material_rate>=0),transport_rate NUMERIC(14,2) NOT NULL CHECK(transport_rate>=0),
 material_amount NUMERIC(26,2) NOT NULL CHECK(material_amount>=0),transport_amount NUMERIC(26,2) NOT NULL CHECK(transport_amount>=0),
 UNIQUE(order_id,position),UNIQUE(order_id,source_rfq_item_id),UNIQUE(id,order_id)
);
INSERT INTO sales_order_items(id,order_id,material_id,position,material,ordered_quantity,material_rate,transport_rate,material_amount,transport_amount)
 SELECT id,id,material_id,1,material,ordered_quantity,CASE WHEN business_type='TRANSPORT' THEN 0 ELSE unit_rate END,CASE WHEN business_type='TRANSPORT' THEN unit_rate ELSE 0 END,
 CASE WHEN business_type='TRANSPORT' THEN 0 ELSE ROUND(ordered_quantity*unit_rate,2) END,CASE WHEN business_type='TRANSPORT' THEN ROUND(ordered_quantity*unit_rate,2) ELSE 0 END FROM sales_orders;
ALTER TABLE dispatches ADD COLUMN order_item_id UUID;
UPDATE dispatches SET order_item_id=order_id;
ALTER TABLE dispatches ALTER COLUMN order_item_id SET NOT NULL;
ALTER TABLE dispatches ADD CONSTRAINT fk_dispatch_order_item FOREIGN KEY(order_item_id,order_id) REFERENCES sales_order_items(id,order_id);
CREATE INDEX ix_rfqs_customer ON rfqs(customer_id);
CREATE INDEX ix_rfqs_project ON rfqs(project_id,customer_id,branch_id);
CREATE INDEX ix_rfqs_site ON rfqs(delivery_site_id,project_id);
CREATE INDEX ix_quote_items_material ON quotation_items(material_id);
CREATE INDEX ix_quote_items_rfq_item ON quotation_items(rfq_item_id);
CREATE INDEX ix_order_items_material ON sales_order_items(material_id);
CREATE INDEX ix_dispatches_order_item ON dispatches(order_item_id,order_id);
