-- In-app notifications shown on the staff Notifications screen (Today / Earlier).
-- occurred_at is the *displayed* time (seeder can back-date it); it's distinct
-- from the audit created_at so the time label + grouping never depend on insert time.
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    type        VARCHAR(28) NOT NULL,
    title       VARCHAR(160) NOT NULL,
    body        VARCHAR(500),
    "read"      BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_employee_occurred ON notifications(employee_id, occurred_at);
