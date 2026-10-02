-- company_settings.work_start_time is a wall-clock "HH:mm", not an instant.
-- As a SQL TIME it went through the JDBC time-zone conversion, so the V18 column
-- default ('09:00') read back shifted. Store it as plain HH:mm text instead.
ALTER TABLE company_settings ALTER COLUMN work_start_time DROP DEFAULT;
ALTER TABLE company_settings
    ALTER COLUMN work_start_time TYPE VARCHAR(5) USING to_char(work_start_time, 'HH24:MI');
ALTER TABLE company_settings ALTER COLUMN work_start_time SET DEFAULT '09:00';
