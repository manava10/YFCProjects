CREATE TABLE customers (
 id UUID PRIMARY KEY, name VARCHAR(160) NOT NULL, mobile VARCHAR(20) NOT NULL,
 gstin VARCHAR(15), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE sales_orders (
 id UUID PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 customer_id UUID NOT NULL REFERENCES customers(id),
 business_type VARCHAR(30) NOT NULL CHECK (business_type IN ('MATERIAL','TRANSPORT','MATERIAL_AND_TRANSPORT')),
 material VARCHAR(160) NOT NULL, delivery_site VARCHAR(500) NOT NULL,
 ordered_quantity NUMERIC(14,3) NOT NULL CHECK (ordered_quantity > 0),
 unit_rate NUMERIC(14,2) NOT NULL CHECK (unit_rate >= 0), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE dispatches (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES sales_orders(id),
 reference VARCHAR(30) NOT NULL UNIQUE,
 loaded_quantity NUMERIC(14,3) NOT NULL CHECK (loaded_quantity > 0),
 received_quantity NUMERIC(14,3) NOT NULL CHECK (received_quantity >= 0)
);
CREATE INDEX ix_orders_customer ON sales_orders(customer_id);
CREATE INDEX ix_dispatches_order ON dispatches(order_id);
