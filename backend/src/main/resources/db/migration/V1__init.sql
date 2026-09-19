-- Teamora core schema. Owned by Flyway; JPA entities validate against it.
-- Postgres 16: gen_random_uuid() is built-in.

-- ---------- Identity ----------
CREATE TABLE employees (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    job_title     VARCHAR(255),
    department    VARCHAR(255),
    location      VARCHAR(255),
    staff_id      VARCHAR(32) UNIQUE,
    phone         VARCHAR(32),
    join_date     DATE,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(200) NOT NULL UNIQUE,
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_employee ON refresh_tokens(employee_id);

-- ---------- Attendance ----------
CREATE TABLE attendance_records (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id   UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date     DATE NOT NULL,
    clock_in_at   TIMESTAMPTZ,
    clock_out_at  TIMESTAMPTZ,
    status        VARCHAR(16) NOT NULL,   -- WORKING, PRESENT, LATE, REMOTE, ON_LEAVE, ABSENT
    worked_minutes INTEGER,
    location      VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_attendance_emp_date UNIQUE (employee_id, work_date)
);
CREATE INDEX idx_attendance_emp_date ON attendance_records(employee_id, work_date);

-- ---------- Leave ----------
CREATE TABLE leave_balances (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type  VARCHAR(16) NOT NULL,   -- ANNUAL, MEDICAL, EMERGENCY
    entitled    INTEGER NOT NULL,
    used        INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_leave_balance_emp_type UNIQUE (employee_id, leave_type)
);

CREATE TABLE leave_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    leave_type  VARCHAR(16) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    days        INTEGER NOT NULL,
    reason      VARCHAR(500),
    status      VARCHAR(16) NOT NULL,   -- PENDING, APPROVED, REJECTED
    decided_by  UUID REFERENCES employees(id),
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_leave_requests_emp ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);

-- ---------- Claims ----------
CREATE TABLE claims (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    category    VARCHAR(32) NOT NULL,   -- TRAVEL, PETROL, MEAL, MEDICAL, OTHER
    title       VARCHAR(255) NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    claim_date  DATE NOT NULL,
    status      VARCHAR(16) NOT NULL,   -- PENDING, APPROVED, REJECTED
    decided_by  UUID REFERENCES employees(id),
    decided_at  TIMESTAMPTZ,
    receipt_url VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_claims_emp ON claims(employee_id);
CREATE INDEX idx_claims_status ON claims(status);

-- ---------- Payroll ----------
CREATE TABLE payslips (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    period      VARCHAR(7) NOT NULL,    -- YYYY-MM
    basic       NUMERIC(12,2) NOT NULL,
    overtime    NUMERIC(12,2) NOT NULL DEFAULT 0,
    claims      NUMERIC(12,2) NOT NULL DEFAULT 0,
    bonus       NUMERIC(12,2) NOT NULL DEFAULT 0,
    gross       NUMERIC(12,2) NOT NULL,
    epf         NUMERIC(12,2) NOT NULL,
    socso       NUMERIC(12,2) NOT NULL,
    eis         NUMERIC(12,2) NOT NULL,
    deductions  NUMERIC(12,2) NOT NULL,
    net         NUMERIC(12,2) NOT NULL,
    pay_date    DATE,
    status      VARCHAR(16) NOT NULL,   -- DRAFT, IN_REVIEW, APPROVED, PAID
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_payslip_emp_period UNIQUE (employee_id, period)
);
CREATE INDEX idx_payslips_emp ON payslips(employee_id);
CREATE INDEX idx_payslips_period ON payslips(period);
