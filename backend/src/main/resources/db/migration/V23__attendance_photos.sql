-- Round 6 (13): move clock-in selfie bytes off attendance_records.
-- The bytes lived inline on every attendance row, so any query that loaded
-- records (live board, dashboard, history) could drag megabytes of photos
-- along. They now live in their own table keyed by the record id and are only
-- read by the photo endpoint. `clock_in_photo_type` stays on the record as the
-- cheap "has a selfie" flag (same pattern as claims.receipt_photo_type).
CREATE TABLE attendance_photos (
    record_id    UUID PRIMARY KEY REFERENCES attendance_records(id) ON DELETE CASCADE,
    company_id   UUID NOT NULL REFERENCES companies(id),
    photo        BYTEA NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendance_photos_company ON attendance_photos(company_id);

INSERT INTO attendance_photos (record_id, company_id, photo, content_type, created_at)
SELECT id, company_id, clock_in_photo, COALESCE(clock_in_photo_type, 'image/jpeg'), COALESCE(clock_in_at, created_at)
FROM attendance_records
WHERE clock_in_photo IS NOT NULL AND octet_length(clock_in_photo) > 0;

-- Keep the flag consistent with what was actually migrated.
UPDATE attendance_records r
SET clock_in_photo_type = p.content_type
FROM attendance_photos p
WHERE p.record_id = r.id;
UPDATE attendance_records
SET clock_in_photo_type = NULL
WHERE clock_in_photo_type IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM attendance_photos p WHERE p.record_id = attendance_records.id);

ALTER TABLE attendance_records DROP COLUMN clock_in_photo;
