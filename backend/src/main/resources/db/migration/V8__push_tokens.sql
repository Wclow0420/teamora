-- Expo push tokens per device, so notifications can fan out to a person's devices.
CREATE TABLE push_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    platform    VARCHAR(16),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_push_tokens_employee ON push_tokens(employee_id);
