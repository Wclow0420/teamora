-- Per-employee tax profile for PCB/MTD (monthly income-tax) estimation, and a
-- PCB column on payslips so net pay can include income tax.
--
-- Tax profile is nullable / defaulted: an employee with no profile is treated as
-- single with no children (the most conservative — highest — PCB).
ALTER TABLE employees ADD COLUMN marital_status VARCHAR(16) NULL;
ALTER TABLE employees ADD COLUMN spouse_working BOOLEAN NULL;
ALTER TABLE employees ADD COLUMN num_children INTEGER NOT NULL DEFAULT 0;

-- Existing payslips predate PCB; default 0 keeps their net consistent.
ALTER TABLE payslips ADD COLUMN pcb NUMERIC(12,2) NOT NULL DEFAULT 0;
