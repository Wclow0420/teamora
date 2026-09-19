-- Overtime requests: an employee logs OT hours for a date; routed for approval
-- exactly like leave/claims (reporting manager, HR/owner override).
CREATE TABLE overtime_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date   DATE NOT NULL,
    hours       NUMERIC(4,1) NOT NULL,
    reason      VARCHAR(500),
    status      VARCHAR(16) NOT NULL,   -- PENDING, APPROVED, REJECTED
    decided_by  UUID REFERENCES employees(id),
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_overtime_employee ON overtime_requests(employee_id);
CREATE INDEX idx_overtime_company_status ON overtime_requests(company_id, status);
