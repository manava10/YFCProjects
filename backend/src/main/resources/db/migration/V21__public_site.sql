CREATE TABLE site_guard(id INTEGER PRIMARY KEY);
INSERT INTO site_guard(id) VALUES (1);
CREATE TABLE site_counters(name VARCHAR(20) PRIMARY KEY, last_number INTEGER NOT NULL);
INSERT INTO site_counters(name, last_number) VALUES ('ticket', 10000);
CREATE TABLE site_profile (
 id INTEGER PRIMARY KEY CHECK(id=1),
 hero_kicker VARCHAR(80) NOT NULL,
 hero_title VARCHAR(120) NOT NULL,
 hero_title_rest VARCHAR(120) NOT NULL,
 hero_subtitle VARCHAR(300) NOT NULL,
 hero_line VARCHAR(200) NOT NULL,
 about_kicker VARCHAR(80) NOT NULL,
 about_title VARCHAR(200) NOT NULL,
 about_paragraph_1 VARCHAR(800) NOT NULL,
 about_paragraph_2 VARCHAR(800) NOT NULL,
 about_paragraph_3 VARCHAR(800) NOT NULL,
 about_image VARCHAR(120) NOT NULL,
 about_caption VARCHAR(80) NOT NULL,
 about_caption_aside VARCHAR(80) NOT NULL,
 contact_kicker VARCHAR(80) NOT NULL,
 contact_title VARCHAR(200) NOT NULL,
 contact_body VARCHAR(1000) NOT NULL,
 contact_tagline VARCHAR(200) NOT NULL,
 footer_line VARCHAR(200) NOT NULL
);
INSERT INTO site_profile VALUES (
 1,
 'SINCE 2004',
 'Building India.',
 'Delivering Trust.',
 'Premium Building Material Supply & Logistics',
 'Since 2004 • 22+ Years • 79,200+ Successful Trips',
 'ABOUT US',
 'Crush. Load. Deliver on a ticket.',
 'YFC Transport has moved graded building material since 2004. The yard crushes, the fleet loads, and every trip leaves with a number: material, tonnes, plant, and drop.',
 'Twenty-two years on the corridor. 79,200 trips completed. Fleet partners run TATA Signa tippers — a working fleet, not a board of strangers.',
 'The desk stays plain. Pick the grade, name the plant, name the site. You leave with a quote you can track from confirmed to delivered.',
 '/media/truck.jpg',
 'TATA Signa class tipper',
 'YFC fleet',
 'CONTACT US',
 'Dispatch desk, Kotputli.',
 'YFC Transport · Premium building material supply & logistics · Since 2004. Pan India delivery from the Kotputli crusher and the NCR yards.',
 'Building India. Delivering Trust.',
 'Since 2004 · Kotputli, Rajasthan · Pan India delivery'
);
CREATE TABLE site_stats (
 id UUID PRIMARY KEY,
 sort_order INTEGER NOT NULL UNIQUE,
 stat_value VARCHAR(40) NOT NULL,
 label VARCHAR(80) NOT NULL
);
INSERT INTO site_stats(id, sort_order, stat_value, label) VALUES
 ('44444444-4444-4444-8444-444444444001', 1, '79,200+', 'Trips Completed'),
 ('44444444-4444-4444-8444-444444444002', 2, '22+', 'Years of Excellence'),
 ('44444444-4444-4444-8444-444444444003', 3, '500+', 'Happy Clients'),
 ('44444444-4444-4444-8444-444444444004', 4, '100+', 'Fleet Partners'),
 ('44444444-4444-4444-8444-444444444005', 5, 'Pan India', 'Delivery');
CREATE TABLE public_materials (
 id UUID PRIMARY KEY,
 sort_order INTEGER NOT NULL UNIQUE,
 name VARCHAR(80) NOT NULL,
 grade VARCHAR(40) NOT NULL,
 use_text VARCHAR(200) NOT NULL,
 rate NUMERIC(12,2) NOT NULL CHECK(rate>=0),
 image_path VARCHAR(120) NOT NULL,
 active BOOLEAN NOT NULL
);
INSERT INTO public_materials(id, sort_order, name, grade, use_text, rate, image_path, active) VALUES
 ('11111111-1111-4111-8111-111111111001', 1, '20 MM Aggregate', '20 mm', 'RCC, foundations, and road base that needs a larger lock.', 820, '/media/agg-20.jpg', TRUE),
 ('11111111-1111-4111-8111-111111111002', 2, '10 MM Aggregate', '10 mm', 'Slabs, precast, and mixes that call for a tighter grade.', 850, '/media/agg-10.jpg', TRUE),
 ('11111111-1111-4111-8111-111111111003', 3, 'VSI Aggregate', 'Cubical', 'Vertical-shaft impact stone. More cubical, cleaner interlock.', 780, '/media/agg-vsi.jpg', TRUE),
 ('11111111-1111-4111-8111-111111111004', 4, 'Stone Dust', 'Fines', 'Filling, blending, and levelling under slabs and pavers.', 650, '/media/agg-dust.jpg', TRUE),
 ('11111111-1111-4111-8111-111111111005', 5, 'M Sand', 'Manufactured sand', 'Plaster and mortar where a graded manufactured sand is specified.', 950, '/media/agg-msand.jpg', TRUE);
CREATE TABLE public_plants (
 id UUID PRIMARY KEY,
 sort_order INTEGER NOT NULL UNIQUE,
 name VARCHAR(80) NOT NULL,
 place VARCHAR(80) NOT NULL,
 note VARCHAR(300) NOT NULL,
 image_path VARCHAR(120) NOT NULL,
 active BOOLEAN NOT NULL
);
INSERT INTO public_plants(id, sort_order, name, place, note, image_path, active) VALUES
 ('22222222-2222-4222-8222-222222222001', 1, 'Kotputli Crusher Plant', 'Kotputli, Rajasthan', 'Primary crush and screen. All five grades load from this yard.', '/media/plant.jpg', TRUE),
 ('22222222-2222-4222-8222-222222222002', 2, 'Behror Crusher Plant', 'Behror, Rajasthan', 'Screening and fast loading on the NH48 run toward NCR.', '/media/hero.jpg', TRUE),
 ('22222222-2222-4222-8222-222222222003', 3, 'Bhiwadi Dispatch Hub', 'Bhiwadi, Rajasthan', 'Staging yard for Gurugram, Manesar, and south Delhi drops.', '/media/truck.jpg', TRUE);
CREATE TABLE public_destinations (
 id UUID PRIMARY KEY,
 sort_order INTEGER NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL UNIQUE,
 active BOOLEAN NOT NULL
);
INSERT INTO public_destinations(id, sort_order, name, active) VALUES
 ('33333333-3333-4333-8333-333333333001', 1, 'Gurugram, Haryana', TRUE),
 ('33333333-3333-4333-8333-333333333002', 2, 'Manesar, Haryana', TRUE),
 ('33333333-3333-4333-8333-333333333003', 3, 'Faridabad, Haryana', TRUE),
 ('33333333-3333-4333-8333-333333333004', 4, 'Delhi', TRUE),
 ('33333333-3333-4333-8333-333333333005', 5, 'Noida, Uttar Pradesh', TRUE),
 ('33333333-3333-4333-8333-333333333006', 6, 'Bhiwadi, Rajasthan', TRUE),
 ('33333333-3333-4333-8333-333333333007', 7, 'Neemrana, Rajasthan', TRUE),
 ('33333333-3333-4333-8333-333333333008', 8, 'Alwar, Rajasthan', TRUE),
 ('33333333-3333-4333-8333-333333333009', 9, 'Jaipur, Rajasthan', TRUE);
CREATE TABLE public_freight (
 plant_id UUID NOT NULL REFERENCES public_plants(id),
 destination_id UUID NOT NULL REFERENCES public_destinations(id),
 rate NUMERIC(12,2) NOT NULL CHECK(rate>=0),
 PRIMARY KEY(plant_id, destination_id)
);
INSERT INTO public_freight(plant_id, destination_id, rate) VALUES
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333001', 420),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333002', 390),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333003', 400),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333004', 460),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333005', 490),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333006', 240),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333007', 180),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333008', 200),
 ('22222222-2222-4222-8222-222222222001', '33333333-3333-4333-8333-333333333009', 260),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333001', 360),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333002', 330),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333003', 350),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333004', 400),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333005', 430),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333006', 160),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333007', 120),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333008', 180),
 ('22222222-2222-4222-8222-222222222002', '33333333-3333-4333-8333-333333333009', 300),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333001', 180),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333002', 150),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333003', 170),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333004', 240),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333005', 280),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333006', 40),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333007', 140),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333008', 220),
 ('22222222-2222-4222-8222-222222222003', '33333333-3333-4333-8333-333333333009', 360);
CREATE TABLE public_enquiries (
 id UUID PRIMARY KEY,
 reference VARCHAR(20) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 phone VARCHAR(20) NOT NULL,
 note VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public_quotes (
 id UUID PRIMARY KEY,
 reference VARCHAR(20) NOT NULL UNIQUE,
 material_id UUID NOT NULL REFERENCES public_materials(id),
 plant_id UUID NOT NULL REFERENCES public_plants(id),
 destination_id UUID NOT NULL REFERENCES public_destinations(id),
 quantity NUMERIC(12,3) NOT NULL CHECK(quantity>0 AND quantity<=500),
 mediator VARCHAR(160) NOT NULL,
 material_rate NUMERIC(12,2) NOT NULL,
 freight_rate NUMERIC(12,2) NOT NULL,
 stage VARCHAR(20) NOT NULL CHECK(stage IN ('CONFIRMED','LOADING','ON_THE_WAY','DELIVERED')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_public_enquiries_created ON public_enquiries(created_at, id);
CREATE INDEX ix_public_quotes_created ON public_quotes(created_at, id);
