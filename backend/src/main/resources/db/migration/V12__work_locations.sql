-- Geofenced clock-in: multi-site work locations + per-staff assignment.
--
-- Adds:
--   * work_locations — company-scoped GPS sites (name, lat/lng pin, radius, active)
--   * employees.work_location_id — nullable assignment (null ⇒ no geofence)
--   * attendance_records.clock_in_lat/lng — captured coordinates for audit
--
-- No seeding: companies define their own sites, and demo employees stay
-- UNASSIGNED so existing seeded attendance / payroll data is unaffected and all
-- existing tests stay green (unassigned staff can still clock in anywhere).

-- ---------- 1. work_locations ----------
CREATE TABLE work_locations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID         NOT NULL REFERENCES companies(id),
    name       VARCHAR(120) NOT NULL,
    latitude   NUMERIC(9,6) NOT NULL,
    longitude  NUMERIC(9,6) NOT NULL,
    radius_m   INT          NOT NULL DEFAULT 100 CHECK (radius_m >= 50),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_work_locations_company ON work_locations(company_id);

-- ---------- 2. employees.work_location_id (nullable ⇒ no geofence) ----------
ALTER TABLE employees ADD COLUMN work_location_id UUID REFERENCES work_locations(id);

-- ---------- 3. attendance_records clock-in coordinates (audit) ----------
ALTER TABLE attendance_records
    ADD COLUMN clock_in_lat NUMERIC(9,6),
    ADD COLUMN clock_in_lng NUMERIC(9,6);
