-- Employee statutory & bank identity fields — needed for real statutory reporting
-- and bank salary payout exports (EPF/SOCSO/EIS/PCB filing + IBG payment).
--
-- All nullable: existing employees keep no identity on record until HR fills them
-- in via the add/edit forms. Exports simply leave the corresponding cell blank
-- when a field is unset (honest empty state — never faked).
ALTER TABLE employees
    ADD COLUMN nric            VARCHAR(20),
    ADD COLUMN epf_no          VARCHAR(32),
    ADD COLUMN socso_no        VARCHAR(32),
    ADD COLUMN tax_no          VARCHAR(32),
    ADD COLUMN bank_name       VARCHAR(64),
    ADD COLUMN bank_account_no VARCHAR(40);
