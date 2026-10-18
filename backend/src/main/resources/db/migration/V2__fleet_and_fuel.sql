CREATE TABLE trucks (
 id UUID PRIMARY KEY, registration VARCHAR(20) NOT NULL UNIQUE,
 capacity_tonnes NUMERIC(10,3) NOT NULL CHECK(capacity_tonnes>0),
 tank_capacity_litres NUMERIC(10,3) NOT NULL CHECK(tank_capacity_litres>0),
 mileage_km_per_litre NUMERIC(8,3) NOT NULL CHECK(mileage_km_per_litre>0)
);
CREATE TABLE diesel_rates (
 id UUID PRIMARY KEY, pump VARCHAR(160) NOT NULL, location VARCHAR(160) NOT NULL,
 effective_date DATE NOT NULL, price_per_litre NUMERIC(10,2) NOT NULL CHECK(price_per_litre>0),
 UNIQUE(pump,location,effective_date)
);
CREATE TABLE fuel_entries (
 id UUID PRIMARY KEY, truck_id UUID NOT NULL REFERENCES trucks(id),rate_id UUID NOT NULL REFERENCES diesel_rates(id),
 receipt_reference VARCHAR(60) NOT NULL UNIQUE, filled_on DATE NOT NULL,
 quantity_litres NUMERIC(12,3) NOT NULL CHECK(quantity_litres>0),
 actual_price_per_litre NUMERIC(10,2) NOT NULL CHECK(actual_price_per_litre>0),
 amount NUMERIC(16,2) NOT NULL CHECK(amount>0)
);
CREATE INDEX ix_fuel_entries_truck ON fuel_entries(truck_id);
