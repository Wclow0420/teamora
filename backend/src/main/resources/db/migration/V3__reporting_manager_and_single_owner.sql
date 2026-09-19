-- Reporting manager (self-reference) + enforce exactly one OWNER per company.

ALTER TABLE employees
    ADD COLUMN reporting_manager_id UUID NULL REFERENCES employees(id);

CREATE INDEX idx_employees_reporting_manager ON employees(reporting_manager_id);

-- Exactly one OWNER per company. Partial unique index: only role='OWNER' rows
-- participate, so any number of non-owners per company is fine. Null
-- reporting_manager_id means "approver defaults to the company owner".
CREATE UNIQUE INDEX uq_employees_one_owner_per_company
    ON employees(company_id)
    WHERE role = 'OWNER';
