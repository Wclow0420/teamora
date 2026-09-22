-- Clock-in selfie: store one front-camera proof photo per attendance row in
-- Postgres (bytea), durable via the existing DB volume. Both columns are
-- nullable so clock-in without a photo (permission denied/unavailable) still
-- works — the photo is optional and non-blocking.
ALTER TABLE attendance_records
    ADD COLUMN clock_in_photo BYTEA,
    ADD COLUMN clock_in_photo_type VARCHAR(32);   -- e.g. 'image/jpeg'
