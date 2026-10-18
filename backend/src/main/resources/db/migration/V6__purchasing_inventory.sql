CREATE TABLE yards (
 id UUID PRIMARY KEY, branch_id UUID NOT NULL REFERENCES branches(id), code VARCHAR(30) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL, address VARCHAR(500) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(id,branch_id), CHECK(code=UPPER(code)), CHECK(LENGTH(TRIM(name))>0)
);
ALTER TABLE supplier_plants ADD CONSTRAINT uq_plant_supplier UNIQUE(id,supplier_id);
ALTER TABLE sales_order_items ADD CONSTRAINT uq_order_item_material UNIQUE(id,material_id);
CREATE TABLE purchase_orders (
 id UUID PRIMARY KEY, supplier_id UUID NOT NULL REFERENCES suppliers(id), plant_id UUID NOT NULL,
 material_id UUID NOT NULL REFERENCES materials(id), branch_id UUID NOT NULL REFERENCES branches(id),
 supplier_name VARCHAR(160) NOT NULL, plant_name VARCHAR(160) NOT NULL, material_name VARCHAR(160) NOT NULL,
 destination VARCHAR(10) NOT NULL CHECK(destination IN ('YARD','DIRECT')), yard_id UUID,
 order_item_id UUID REFERENCES sales_order_items(id),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), rate NUMERIC(14,2) NOT NULL CHECK(rate>=0),
 subtotal NUMERIC(26,2) NOT NULL CHECK(subtotal>=0), expected_date DATE NOT NULL, terms VARCHAR(1000) NOT NULL,
 received_quantity NUMERIC(14,3) NOT NULL DEFAULT 0, accepted_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
 status VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RECEIVED','CLOSED','CANCELLED')),
 close_reason VARCHAR(1000), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(order_item_id,material_id) REFERENCES sales_order_items(id,material_id),
 FOREIGN KEY(plant_id,supplier_id) REFERENCES supplier_plants(id,supplier_id),
 FOREIGN KEY(yard_id,branch_id) REFERENCES yards(id,branch_id),
 CHECK((destination='YARD' AND yard_id IS NOT NULL AND order_item_id IS NULL) OR
 (destination='DIRECT' AND yard_id IS NULL AND order_item_id IS NOT NULL)),
 CHECK(received_quantity>=0 AND received_quantity<=quantity),
 CHECK(accepted_quantity>=0 AND accepted_quantity<=received_quantity),
 CHECK(status<>'RECEIVED' OR received_quantity=quantity),
 CHECK(status NOT IN ('CLOSED','CANCELLED') OR (close_reason IS NOT NULL AND LENGTH(TRIM(close_reason))>0)),
 CHECK(status<>'CANCELLED' OR received_quantity=0)
);
CREATE INDEX ix_purchase_allocation ON purchase_orders(order_item_id,status);
CREATE TABLE stock_balances (
 yard_id UUID NOT NULL REFERENCES yards(id), material_id UUID NOT NULL REFERENCES materials(id),
 on_hand NUMERIC(19,3) NOT NULL DEFAULT 0, reserved NUMERIC(19,3) NOT NULL DEFAULT 0,
 PRIMARY KEY(yard_id,material_id), CHECK(on_hand>=0 AND reserved>=0 AND reserved<=on_hand)
);
CREATE TABLE purchase_receipts (
 id UUID PRIMARY KEY, purchase_id UUID NOT NULL REFERENCES purchase_orders(id), received_date DATE NOT NULL,
 loaded_quantity NUMERIC(14,3) NOT NULL CHECK(loaded_quantity>0),
 received_quantity NUMERIC(14,3) NOT NULL CHECK(received_quantity>0),
 accepted_quantity NUMERIC(14,3) NOT NULL CHECK(accepted_quantity>=0 AND accepted_quantity<=received_quantity),
 reference VARCHAR(300) NOT NULL, discrepancy_reason VARCHAR(1000),
 recorded_by VARCHAR(160) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK((loaded_quantity=received_quantity AND received_quantity=accepted_quantity) OR (discrepancy_reason IS NOT NULL AND LENGTH(TRIM(discrepancy_reason))>0))
);
CREATE INDEX ix_receipts_purchase ON purchase_receipts(purchase_id);
CREATE TABLE stock_reservations (
 id UUID PRIMARY KEY, yard_id UUID NOT NULL, material_id UUID NOT NULL,
 order_item_id UUID NOT NULL REFERENCES sales_order_items(id), quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 issued_quantity NUMERIC(14,3) NOT NULL DEFAULT 0, released_quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
 release_reason VARCHAR(1000), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(yard_id,material_id) REFERENCES stock_balances(yard_id,material_id),
 FOREIGN KEY(order_item_id,material_id) REFERENCES sales_order_items(id,material_id),
 CHECK(issued_quantity>=0 AND released_quantity>=0 AND issued_quantity+released_quantity<=quantity),
 CHECK(released_quantity=0 OR (release_reason IS NOT NULL AND LENGTH(TRIM(release_reason))>0))
);
CREATE INDEX ix_reservations_order ON stock_reservations(order_item_id);
CREATE TABLE stock_movements (
 id UUID PRIMARY KEY, yard_id UUID NOT NULL, material_id UUID NOT NULL,
 kind VARCHAR(10) NOT NULL CHECK(kind IN ('RECEIPT','ISSUE')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0), receipt_id UUID UNIQUE REFERENCES purchase_receipts(id),
 reservation_id UUID REFERENCES stock_reservations(id), reference VARCHAR(300) NOT NULL,
 recorded_by VARCHAR(160) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(yard_id,material_id) REFERENCES stock_balances(yard_id,material_id),
 CHECK((kind='RECEIPT' AND receipt_id IS NOT NULL AND reservation_id IS NULL) OR
 (kind='ISSUE' AND receipt_id IS NULL AND reservation_id IS NOT NULL))
);
CREATE INDEX ix_movements_stock ON stock_movements(yard_id,material_id,created_at);
CREATE INDEX ix_movements_reservation ON stock_movements(reservation_id);
