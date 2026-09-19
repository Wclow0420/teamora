-- Weekly shift roster: each employee has at most one shift per date.
CREATE TABLE shifts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date   DATE NOT NULL,
    shift_type  VARCHAR(16) NOT NULL,   -- MORNING, EVENING, REMOTE, OFF
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_shift_emp_date UNIQUE (employee_id, work_date)
);
CREATE INDEX idx_shifts_company_date ON shifts(company_id, work_date);
