CREATE TABLE branches (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,code VARCHAR(30) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,address VARCHAR(500) NOT NULL,city VARCHAR(100) NOT NULL,state VARCHAR(100) NOT NULL,pincode VARCHAR(6) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE materials (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,code VARCHAR(30) NOT NULL UNIQUE,name VARCHAR(160) NOT NULL,
 category VARCHAR(30) NOT NULL CHECK(category IN ('AGGREGATES','SAND','DUST','ROAD_MATERIALS','OTHER')),
 unit VARCHAR(20) NOT NULL CHECK(unit='TONNE'),hsn_code VARCHAR(8),active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE customer_projects (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,customer_id UUID NOT NULL REFERENCES customers(id),branch_id UUID NOT NULL REFERENCES branches(id),
 code VARCHAR(30) NOT NULL,name VARCHAR(160) NOT NULL,description VARCHAR(500),active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(customer_id,code),UNIQUE(id,customer_id,branch_id),CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE delivery_sites (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,project_id UUID NOT NULL REFERENCES customer_projects(id),
 code VARCHAR(30) NOT NULL,name VARCHAR(160) NOT NULL,address VARCHAR(500) NOT NULL,contact_name VARCHAR(160),mobile VARCHAR(20),
 active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(project_id,code),UNIQUE(id,project_id),CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE suppliers (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,code VARCHAR(30) NOT NULL UNIQUE,name VARCHAR(160) NOT NULL,gstin VARCHAR(15) NOT NULL UNIQUE,
 mobile VARCHAR(20) NOT NULL,email VARCHAR(254),address VARCHAR(500) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE supplier_plants (
 id UUID PRIMARY KEY,version BIGINT NOT NULL DEFAULT 0,supplier_id UUID NOT NULL REFERENCES suppliers(id),
 code VARCHAR(30) NOT NULL,name VARCHAR(160) NOT NULL,address VARCHAR(500) NOT NULL,contact_name VARCHAR(160),mobile VARCHAR(20),
 active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(supplier_id,code),CHECK(code=UPPER(code)),CHECK(LENGTH(TRIM(name))>0)
);
CREATE TABLE plant_materials (
 id UUID PRIMARY KEY,plant_id UUID NOT NULL REFERENCES supplier_plants(id),material_id UUID NOT NULL REFERENCES materials(id),UNIQUE(plant_id,material_id)
);
CREATE INDEX ix_projects_branch ON customer_projects(branch_id);
CREATE INDEX ix_plant_materials_material ON plant_materials(material_id);
ALTER TABLE sales_orders ADD COLUMN branch_id UUID REFERENCES branches(id);
ALTER TABLE sales_orders ADD COLUMN project_id UUID;
ALTER TABLE sales_orders ADD COLUMN delivery_site_id UUID;
ALTER TABLE sales_orders ADD COLUMN material_id UUID REFERENCES materials(id);
ALTER TABLE sales_orders ADD CONSTRAINT fk_order_project_customer_branch FOREIGN KEY(project_id,customer_id,branch_id) REFERENCES customer_projects(id,customer_id,branch_id);
ALTER TABLE sales_orders ADD CONSTRAINT fk_order_site_project FOREIGN KEY(delivery_site_id,project_id) REFERENCES delivery_sites(id,project_id);
ALTER TABLE sales_orders ADD CONSTRAINT ck_order_location CHECK((delivery_site_id IS NULL AND project_id IS NULL) OR (delivery_site_id IS NOT NULL AND project_id IS NOT NULL AND branch_id IS NOT NULL));
CREATE INDEX ix_orders_branch ON sales_orders(branch_id);
CREATE INDEX ix_orders_material ON sales_orders(material_id);
CREATE INDEX ix_orders_project_customer_branch ON sales_orders(project_id,customer_id,branch_id);
CREATE INDEX ix_orders_site_project ON sales_orders(delivery_site_id,project_id);
