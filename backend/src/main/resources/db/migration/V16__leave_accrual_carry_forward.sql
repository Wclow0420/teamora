-- Leave accrual, carry-forward & join-date proration (Phase 2).
--
-- Balances gain a LEAVE YEAR dimension so entitlement can reset (and roll over)
-- each year instead of being one flat row per (employee, leave type) forever:
--
--   * company_settings.leave_year_start_month — 1 = calendar year (the default),
--     4 = an April–March leave year, etc.
--   * leave_types.carry_forward_max_days — cap on unused days rolled into the
--     next leave year (0 = unused days are forfeited at year end).
--   * leave_balances.leave_year / carried_forward — the year the row belongs to
--     and what it inherited from the year before.
--
-- Existing rows predate the concept, so they are backfilled to the CURRENT leave
-- year. Every company is on start month 1 at this point (the column is added with
-- default 1 in this same migration), so the current leave year is simply the
-- current calendar year.

-- ---------- 1. Company-configurable leave year ----------
ALTER TABLE company_settings
    ADD COLUMN leave_year_start_month INT NOT NULL DEFAULT 1;

ALTER TABLE company_settings
    ADD CONSTRAINT ck_company_settings_leave_year_start_month
        CHECK (leave_year_start_month BETWEEN 1 AND 12);

-- ---------- 2. Per-type carry-forward cap ----------
ALTER TABLE leave_types
    ADD COLUMN carry_forward_max_days NUMERIC(5,2) NOT NULL DEFAULT 0;

-- ---------- 3. Year-scoped balances ----------
ALTER TABLE leave_balances
    ADD COLUMN leave_year      INT,
    ADD COLUMN carried_forward NUMERIC(6,2) NOT NULL DEFAULT 0;

UPDATE leave_balances
   SET leave_year = EXTRACT(YEAR FROM CURRENT_DATE)::int
 WHERE leave_year IS NULL;

ALTER TABLE leave_balances ALTER COLUMN leave_year SET NOT NULL;

-- Replace the year-blind uniqueness (added as a table constraint in V11) with a
-- year-scoped unique index, so an employee holds one row per type PER YEAR.
ALTER TABLE leave_balances DROP CONSTRAINT IF EXISTS uq_leave_balance_emp_type;

CREATE UNIQUE INDEX uq_leave_balance_emp_type_year
    ON leave_balances (employee_id, leave_type_id, leave_year);
