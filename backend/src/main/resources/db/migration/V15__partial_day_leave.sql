-- Partial-day leave (half-day & hourly).
--
-- Leave stops being whole-day: a request now carries a duration unit
-- (FULL_DAY | HALF_DAY | HOURS) and a fractional day count. Balances and the
-- payslip's unpaid-day figure widen to match, because those fractions feed the
-- payroll deduction (dailyRate x unpaidFraction) to the sen.
--
-- Existing rows convert cleanly: INTEGER -> NUMERIC keeps the same value, and
-- duration_unit defaults to FULL_DAY so historic requests stay valid.

ALTER TABLE leave_requests
    ALTER COLUMN days TYPE NUMERIC(5,2) USING days::numeric,
    ADD COLUMN duration_unit   VARCHAR(16) NOT NULL DEFAULT 'FULL_DAY',
    ADD COLUMN half_day_period VARCHAR(2),   -- 'AM' | 'PM', null unless HALF_DAY
    ADD COLUMN start_time      TIME,         -- optional, HOURS only
    ADD COLUMN hours           NUMERIC(4,2); -- null unless HOURS

ALTER TABLE leave_balances
    ALTER COLUMN entitled TYPE NUMERIC(6,2) USING entitled::numeric,
    ALTER COLUMN used     TYPE NUMERIC(6,2) USING used::numeric;

ALTER TABLE payslips
    ALTER COLUMN unpaid_days TYPE NUMERIC(5,2) USING unpaid_days::numeric;
