-- Round 3.
-- (A) Forgot password: one-time 6-digit codes. Only a hash of the code is stored.
CREATE TABLE password_reset_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    attempts    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_password_reset_codes_employee ON password_reset_codes(employee_id, created_at DESC);

-- (B) Clock in again after clocking out: minutes spent clocked-out mid-day, so
-- worked time stays net of breaks.
ALTER TABLE attendance_records ADD COLUMN break_minutes INT NOT NULL DEFAULT 0;
