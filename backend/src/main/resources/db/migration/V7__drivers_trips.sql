ALTER TABLE trucks ADD COLUMN branch_id UUID REFERENCES branches(id);
ALTER TABLE trucks ADD COLUMN owner_name VARCHAR(160);
ALTER TABLE trucks ADD COLUMN truck_type VARCHAR(80);
ALTER TABLE trucks ADD COLUMN rc_number VARCHAR(80);
ALTER TABLE trucks ADD COLUMN insurance_number VARCHAR(80);
ALTER TABLE trucks ADD COLUMN insurance_expiry DATE;
ALTER TABLE trucks ADD COLUMN fitness_expiry DATE;
ALTER TABLE trucks ADD COLUMN permit_expiry DATE;
ALTER TABLE trucks ADD COLUMN pollution_expiry DATE;
ALTER TABLE trucks ADD COLUMN operational_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE' CHECK(operational_status IN ('ACTIVE','INACTIVE','MAINTENANCE'));
CREATE TABLE drivers (
 id UUID PRIMARY KEY, branch_id UUID NOT NULL REFERENCES branches(id), name VARCHAR(160) NOT NULL,
 mobile VARCHAR(20) NOT NULL, license_number VARCHAR(40) NOT NULL UNIQUE, license_expiry DATE NOT NULL,
 emergency_contact VARCHAR(160) NOT NULL, emergency_mobile VARCHAR(20) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE fleet_assignments (
 truck_id UUID PRIMARY KEY REFERENCES trucks(id), driver_id UUID NOT NULL UNIQUE REFERENCES drivers(id),
 trip_id UUID NOT NULL UNIQUE
);
ALTER TABLE sales_order_items ADD COLUMN fulfillment_mode VARCHAR(10) NOT NULL DEFAULT 'LEGACY' CHECK(fulfillment_mode IN ('LEGACY','TRIP'));
ALTER TABLE stock_reservations ADD CONSTRAINT uq_reservation_item UNIQUE(id,order_item_id);
ALTER TABLE purchase_orders ADD CONSTRAINT uq_purchase_item UNIQUE(id,order_item_id);
CREATE TABLE trip_supply_claims (
 trip_id UUID PRIMARY KEY, order_item_id UUID NOT NULL REFERENCES sales_order_items(id),
 reservation_id UUID REFERENCES stock_reservations(id), purchase_id UUID REFERENCES purchase_orders(id),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity>0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('CLAIMED','IN_TRANSIT','DELIVERED','CANCELLED')),
 FOREIGN KEY(reservation_id,order_item_id) REFERENCES stock_reservations(id,order_item_id),
 FOREIGN KEY(purchase_id,order_item_id) REFERENCES purchase_orders(id,order_item_id),
 CHECK((reservation_id IS NOT NULL AND purchase_id IS NULL) OR (reservation_id IS NULL AND purchase_id IS NOT NULL))
);
CREATE INDEX ix_trip_claim_reservation ON trip_supply_claims(reservation_id,status);
CREATE INDEX ix_trip_claim_purchase ON trip_supply_claims(purchase_id,status);
CREATE TABLE trips (
 id UUID PRIMARY KEY, order_item_id UUID NOT NULL REFERENCES sales_order_items(id),
 truck_id UUID NOT NULL REFERENCES trucks(id), driver_id UUID NOT NULL REFERENCES drivers(id),
 source VARCHAR(15) NOT NULL CHECK(source IN ('YARD','DIRECT','TRANSPORT')),
 reservation_id UUID REFERENCES stock_reservations(id), purchase_id UUID REFERENCES purchase_orders(id),
 planned_quantity NUMERIC(14,3) NOT NULL CHECK(planned_quantity>0), planned_date DATE NOT NULL,
 distance_km NUMERIC(12,3) NOT NULL CHECK(distance_km>0),
 estimated_fuel_litres NUMERIC(19,3) NOT NULL CHECK(estimated_fuel_litres>0),
 truck_registration VARCHAR(20) NOT NULL, driver_name VARCHAR(160) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('ALLOCATED','LOADING','LOADED','DISPATCHED','ARRIVED','UNLOADING','DELIVERED','CANCELLED')),
 create_payload TEXT NOT NULL,
 loaded_gross NUMERIC(14,3),loaded_tare NUMERIC(14,3),loaded_quantity NUMERIC(14,3),loading_slip VARCHAR(100),
 received_gross NUMERIC(14,3),received_tare NUMERIC(14,3),received_quantity NUMERIC(14,3),accepted_quantity NUMERIC(14,3),
 delivery_slip VARCHAR(100),receiver_name VARCHAR(160),proof_reference VARCHAR(300),discrepancy_reason VARCHAR(1000),cancel_reason VARCHAR(1000),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 loaded_at TIMESTAMP WITH TIME ZONE,dispatched_at TIMESTAMP WITH TIME ZONE,delivered_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(id,truck_id,driver_id), UNIQUE(id,order_item_id),
 FOREIGN KEY(reservation_id,order_item_id) REFERENCES stock_reservations(id,order_item_id),
 FOREIGN KEY(purchase_id,order_item_id) REFERENCES purchase_orders(id,order_item_id),
 CHECK((source='YARD' AND reservation_id IS NOT NULL AND purchase_id IS NULL) OR
 (source='DIRECT' AND reservation_id IS NULL AND purchase_id IS NOT NULL) OR
 (source='TRANSPORT' AND reservation_id IS NULL AND purchase_id IS NULL)),
 CHECK(loaded_quantity IS NULL OR (loaded_gross IS NOT NULL AND loaded_tare IS NOT NULL AND loaded_quantity>0 AND loaded_quantity<=planned_quantity AND loaded_gross>loaded_tare AND loaded_quantity=loaded_gross-loaded_tare)),
 CHECK(received_quantity IS NULL OR (received_gross IS NOT NULL AND received_tare IS NOT NULL AND accepted_quantity IS NOT NULL AND received_quantity>0 AND received_quantity<=loaded_quantity AND received_gross>received_tare AND received_quantity=received_gross-received_tare AND accepted_quantity>=0 AND accepted_quantity<=received_quantity)),
 CHECK(status NOT IN ('LOADED','DISPATCHED','ARRIVED','UNLOADING','DELIVERED') OR loaded_quantity IS NOT NULL),
 CHECK(received_quantity IS NULL OR (loaded_quantity=received_quantity AND received_quantity=accepted_quantity) OR (discrepancy_reason IS NOT NULL AND LENGTH(TRIM(discrepancy_reason))>0)),
 CHECK(status<>'DELIVERED' OR (received_quantity IS NOT NULL AND receiver_name IS NOT NULL AND proof_reference IS NOT NULL AND delivered_at IS NOT NULL)),
 CHECK(status<>'CANCELLED' OR (cancel_reason IS NOT NULL AND LENGTH(TRIM(cancel_reason))>0))
);
CREATE INDEX ix_trips_order ON trips(order_item_id,status);
CREATE INDEX ix_trips_truck ON trips(truck_id,created_at);
ALTER TABLE fleet_assignments ADD CONSTRAINT fk_assignment_trip FOREIGN KEY(trip_id,truck_id,driver_id) REFERENCES trips(id,truck_id,driver_id);
ALTER TABLE trip_supply_claims ADD CONSTRAINT fk_supply_trip FOREIGN KEY(trip_id,order_item_id) REFERENCES trips(id,order_item_id);
CREATE TABLE trip_events (
 id UUID PRIMARY KEY, trip_id UUID NOT NULL REFERENCES trips(id), action VARCHAR(30) NOT NULL,
 payload TEXT NOT NULL, actor VARCHAR(80) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_trip_events_trip ON trip_events(trip_id,occurred_at);
