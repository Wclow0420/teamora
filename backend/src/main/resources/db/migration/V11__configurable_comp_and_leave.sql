-- Configurable compensation + leave engine.
--
-- Adds:
--   * company_settings — per-company payroll/schedule defaults (1:1 with companies)
--   * employees comp/schedule override columns (null ⇒ fall back to company default)
--   * leave_types — company-scoped leave catalogue, replacing the hardcoded enum
--   * leave_requests / leave_balances converted from the leave_type VARCHAR enum to
--     a leave_type_id FK (backfilled by matching code within the same company)
--   * payslips transparency columns (unpaid days/deduction, paid days, daily rate)
--
-- On a fresh database the `companies` table is empty at migration time (the demo
-- companies are created by the runtime seeders), so the per-company seeds/backfills
-- below simply affect zero rows and the seeders populate everything. On an existing
-- deployment they seed defaults for the real companies and backfill live leave rows.

-- ---------- 1. company_settings ----------
CREATE TABLE company_settings (
    company_id            UUID PRIMARY KEY REFERENCES companies(id),
    default_pay_basis     VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY',
    default_working_days  SMALLINT     NOT NULL DEFAULT 31,   -- weekday bitmask: bit0=Mon..bit6=Sun; 31 = Mon–Fri
    default_hours_per_day NUMERIC(4,2) NOT NULL DEFAULT 8.00,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO company_settings (company_id, default_pay_basis, default_working_days, default_hours_per_day, created_at, updated_at)
SELECT id, 'MONTHLY', 31, 8.00, now(), now() FROM companies;

-- ---------- 2. employees comp/schedule overrides (null ⇒ company default) ----------
ALTER TABLE employees ADD COLUMN pay_basis     VARCHAR(16);
ALTER TABLE employees ADD COLUMN working_days  SMALLINT;
ALTER TABLE employees ADD COLUMN hours_per_day NUMERIC(4,2);

-- ---------- 3. leave_types (company-scoped) ----------
CREATE TABLE leave_types (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id               UUID NOT NULL REFERENCES companies(id),
    name                     VARCHAR(64)  NOT NULL,
    code                     VARCHAR(24)  NOT NULL,            -- stable key: ANNUAL, MEDICAL, EMERGENCY, UNPAID, …
    paid                     BOOLEAN      NOT NULL DEFAULT TRUE,
    default_entitlement_days INT          NOT NULL DEFAULT 0,
    accrual                  VARCHAR(16)  NOT NULL DEFAULT 'FIXED_ANNUAL', -- FIXED_ANNUAL | MONTHLY_ACCRUAL | NONE
    color_key                VARCHAR(16)  NOT NULL DEFAULT 'coral',
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order               INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_leave_types_company_code UNIQUE (company_id, code)
);
CREATE INDEX idx_leave_types_company ON leave_types(company_id);

-- Seed the default catalogue per existing company. All paid except Unpaid Leave,
-- so existing (paid) leave usage never reduces payroll numbers.
INSERT INTO leave_types (id, company_id, name, code, paid, default_entitlement_days, accrual, color_key, active, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), c.id, 'Annual Leave',    'ANNUAL',    TRUE,  16, 'FIXED_ANNUAL', 'coral',  TRUE, 1, now(), now() FROM companies c
UNION ALL
SELECT gen_random_uuid(), c.id, 'Medical Leave',   'MEDICAL',   TRUE,  14, 'FIXED_ANNUAL', 'sage',   TRUE, 2, now(), now() FROM companies c
UNION ALL
SELECT gen_random_uuid(), c.id, 'Emergency Leave', 'EMERGENCY', TRUE,   5, 'FIXED_ANNUAL', 'amber',  TRUE, 3, now(), now() FROM companies c
UNION ALL
SELECT gen_random_uuid(), c.id, 'Unpaid Leave',    'UNPAID',    FALSE,  0, 'NONE',         'violet', TRUE, 4, now(), now() FROM companies c;

-- ---------- 4. Convert leave_requests & leave_balances: enum → FK ----------
ALTER TABLE leave_requests ADD COLUMN leave_type_id UUID REFERENCES leave_types(id);
ALTER TABLE leave_balances ADD COLUMN leave_type_id UUID REFERENCES leave_types(id);

-- Backfill by matching the old VARCHAR code to the seeded leave type in the same company.
UPDATE leave_requests r SET leave_type_id = (
    SELECT lt.id FROM leave_types lt WHERE lt.company_id = r.company_id AND lt.code = r.leave_type
);
UPDATE leave_balances b SET leave_type_id = (
    SELECT lt.id FROM leave_types lt WHERE lt.company_id = b.company_id AND lt.code = b.leave_type
);

ALTER TABLE leave_requests ALTER COLUMN leave_type_id SET NOT NULL;
ALTER TABLE leave_balances ALTER COLUMN leave_type_id SET NOT NULL;

-- Drop the old enum columns and swap the balances uniqueness onto the FK.
ALTER TABLE leave_balances DROP CONSTRAINT IF EXISTS uq_leave_balance_emp_type;
ALTER TABLE leave_requests DROP COLUMN leave_type;
ALTER TABLE leave_balances DROP COLUMN leave_type;
ALTER TABLE leave_balances ADD CONSTRAINT uq_leave_balance_emp_type UNIQUE (employee_id, leave_type_id);

CREATE INDEX idx_leave_requests_type ON leave_requests(leave_type_id);
CREATE INDEX idx_leave_balances_type ON leave_balances(leave_type_id);

-- ---------- 5. payslips transparency columns ----------
ALTER TABLE payslips ADD COLUMN unpaid_days      INT          NOT NULL DEFAULT 0;
ALTER TABLE payslips ADD COLUMN unpaid_deduction NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE payslips ADD COLUMN paid_days        INT;
ALTER TABLE payslips ADD COLUMN daily_rate       NUMERIC(12,2);
