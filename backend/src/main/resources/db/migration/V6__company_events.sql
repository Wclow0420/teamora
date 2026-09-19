-- Company calendar events: company-owned dated entries (holidays, town halls,
-- birthdays, product launches) rendered as coloured dots on the staff calendar.
CREATE TABLE company_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id),
    title       VARCHAR(255) NOT NULL,
    event_date  DATE NOT NULL,
    event_type  VARCHAR(16) NOT NULL,   -- HOLIDAY, EVENT, TOWNHALL, BIRTHDAY
    time_label  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_company_events_company_date ON company_events(company_id, event_date);
