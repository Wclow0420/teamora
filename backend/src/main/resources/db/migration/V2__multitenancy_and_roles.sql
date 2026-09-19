-- Multi-tenancy: introduce the company (employer) as the tenant root, scope all
-- existing tables to a company, and migrate the role values to the new hierarchy.

-- ---------- Company (employer / tenant) ----------
CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(120) NOT NULL UNIQUE,
    registration_no VARCHAR(64),   -- SSM company no.
    epf_no          VARCHAR(64),   -- employer EPF no.
    socso_no        VARCHAR(64),   -- employer SOCSO no.
    email           VARCHAR(255),
    phone           VARCHAR(32),
    address         VARCHAR(512),
    timezone        VARCHAR(64) NOT NULL DEFAULT 'Asia/Kuala_Lumpur',
    currency        VARCHAR(8)  NOT NULL DEFAULT 'MYR',
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- ---------- Add company_id to every tenant-scoped table (nullable for backfill) ----------
ALTER TABLE employees          ADD COLUMN company_id UUID;
ALTER TABLE attendance_records ADD COLUMN company_id UUID;
ALTER TABLE leave_balances     ADD COLUMN company_id UUID;
ALTER TABLE leave_requests     ADD COLUMN company_id UUID;
ALTER TABLE claims             ADD COLUMN company_id UUID;
ALTER TABLE payslips           ADD COLUMN company_id UUID;

-- ---------- Migrate role values + backfill a default company for any existing data ----------
-- (On a fresh DB these affect zero rows; the seeders create the real demo companies.)
DO $$
DECLARE cid UUID;
BEGIN
    UPDATE employees SET role = 'HR_ADMIN' WHERE role = 'ADMIN';
    UPDATE employees SET role = 'EMPLOYEE' WHERE role = 'STAFF';

    IF EXISTS (SELECT 1 FROM employees WHERE company_id IS NULL) THEN
        INSERT INTO companies (id, name, slug, currency, timezone, active, created_at, updated_at)
        VALUES (gen_random_uuid(), 'Lumi Foods Sdn Bhd', 'lumi-foods', 'MYR', 'Asia/Kuala_Lumpur', TRUE, now(), now())
        RETURNING id INTO cid;

        UPDATE employees          SET company_id = cid WHERE company_id IS NULL;
        UPDATE attendance_records SET company_id = cid WHERE company_id IS NULL;
        UPDATE leave_balances     SET company_id = cid WHERE company_id IS NULL;
        UPDATE leave_requests     SET company_id = cid WHERE company_id IS NULL;
        UPDATE claims             SET company_id = cid WHERE company_id IS NULL;
        UPDATE payslips           SET company_id = cid WHERE company_id IS NULL;
    END IF;
END $$;

-- ---------- Enforce NOT NULL + FKs + indexes ----------
ALTER TABLE employees          ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE attendance_records ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE leave_balances     ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE leave_requests     ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE claims             ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE payslips           ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE employees          ADD CONSTRAINT fk_employees_company          FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE attendance_records ADD CONSTRAINT fk_attendance_company         FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE leave_balances     ADD CONSTRAINT fk_leave_balances_company     FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE leave_requests     ADD CONSTRAINT fk_leave_requests_company     FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE claims             ADD CONSTRAINT fk_claims_company             FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE payslips           ADD CONSTRAINT fk_payslips_company           FOREIGN KEY (company_id) REFERENCES companies(id);

CREATE INDEX idx_employees_company  ON employees(company_id);
CREATE INDEX idx_attendance_company ON attendance_records(company_id);
CREATE INDEX idx_leave_bal_company  ON leave_balances(company_id);
CREATE INDEX idx_leave_req_company  ON leave_requests(company_id);
CREATE INDEX idx_claims_company     ON claims(company_id);
CREATE INDEX idx_payslips_company   ON payslips(company_id);

-- Email stays globally unique (login is by email across all tenants).
