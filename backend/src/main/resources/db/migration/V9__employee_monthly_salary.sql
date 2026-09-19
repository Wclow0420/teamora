-- Monthly basic salary per employee — the basis for payroll runs.
-- Nullable: existing/unset employees have no salary and are skipped by a run
-- until HR sets one. NUMERIC(12,2) to match the money columns on payslips.
ALTER TABLE employees ADD COLUMN monthly_salary NUMERIC(12,2) NULL;
