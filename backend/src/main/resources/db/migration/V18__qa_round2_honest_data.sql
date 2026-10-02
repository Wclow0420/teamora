-- QA round 2 — honest data + configurable lateness.
--
--   * company_settings.work_start_time / late_grace_minutes — a clock-in is LATE
--     once it is after work_start_time + late_grace_minutes (was a fixed 09:05).
--   * leave_balances.entitlement_overridden — true once an admin has set the
--     entitlement by hand, so a later join-date change never re-prorates over it.
--   * employees.staff_id — unique per COMPANY (was globally unique, which leaked
--     across tenants: company B could not reuse company A's "EMP-001").
--   * attendance_records.location — drop the old hard-coded default site name
--     where the company has no work location of that name (it was never real).

-- ---------- 1. Work start time + late grace ----------
ALTER TABLE company_settings
    ADD COLUMN work_start_time    TIME NOT NULL DEFAULT '09:00',
    ADD COLUMN late_grace_minutes INT  NOT NULL DEFAULT 5;

ALTER TABLE company_settings
    ADD CONSTRAINT ck_company_settings_late_grace_minutes
        CHECK (late_grace_minutes BETWEEN 0 AND 120);

-- ---------- 2. Manual entitlement override marker ----------
ALTER TABLE leave_balances
    ADD COLUMN entitlement_overridden BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------- 3. Staff id unique within a company ----------
ALTER TABLE employees DROP CONSTRAINT IF EXISTS employees_staff_id_key;
CREATE UNIQUE INDEX uq_employees_company_staff_id ON employees (company_id, staff_id);

-- ---------- 4. Remove the fake default clock-in location ----------
-- attendance_records.location is already nullable (V1).
UPDATE attendance_records a
SET location = NULL
WHERE a.location = 'Bangsar South HQ'
  AND NOT EXISTS (
      SELECT 1 FROM work_locations w
      WHERE w.company_id = a.company_id AND w.name = a.location
  );
