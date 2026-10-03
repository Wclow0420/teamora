-- Round 6 (15): audit trail for sensitive admin actions — bank details, salary and
-- role changes, admin password resets, deactivate/reactivate, payroll approve /
-- mark-paid. `details` holds the before/after with sensitive values masked
-- (bank account numbers show only their last 4 digits). Rows are append-only.
CREATE TABLE audit_events (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID NOT NULL REFERENCES companies(id),
    actor_id           UUID REFERENCES employees(id) ON DELETE SET NULL,
    target_employee_id UUID REFERENCES employees(id) ON DELETE SET NULL,
    action             VARCHAR(48) NOT NULL,
    details            JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_company_created ON audit_events(company_id, created_at DESC);
CREATE INDEX idx_audit_events_target ON audit_events(target_employee_id, created_at DESC);
