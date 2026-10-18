CREATE TABLE trip_incidents (
 id UUID PRIMARY KEY, trip_id UUID NOT NULL REFERENCES trips(id),
 category VARCHAR(20) NOT NULL CHECK(category IN ('BREAKDOWN','ACCIDENT','DELAY','SHORTAGE','DAMAGE','OTHER')),
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, description VARCHAR(2000) NOT NULL CHECK(LENGTH(TRIM(description))>0),
 evidence_reference VARCHAR(500) NOT NULL CHECK(LENGTH(TRIM(evidence_reference))>0),
 reported_by VARCHAR(100) NOT NULL, reported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 create_payload TEXT NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','RESOLVED')),
 resolution_id UUID UNIQUE, resolution_payload TEXT, resolution VARCHAR(2000), resolution_evidence VARCHAR(500),
 resolved_by VARCHAR(100), resolved_at TIMESTAMP WITH TIME ZONE,
 CHECK((status='OPEN' AND resolution_id IS NULL AND resolution_payload IS NULL AND resolution IS NULL AND resolution_evidence IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
 OR (status='RESOLVED' AND resolution_id IS NOT NULL AND resolution_payload IS NOT NULL AND resolution IS NOT NULL AND LENGTH(TRIM(resolution))>0 AND resolution_evidence IS NOT NULL AND LENGTH(TRIM(resolution_evidence))>0 AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE INDEX ix_trip_incidents_trip ON trip_incidents(trip_id,reported_at,id);
CREATE INDEX ix_trip_incidents_status ON trip_incidents(status,reported_at,id);
