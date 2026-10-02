-- Claim receipts: store one receipt photo per claim inline in Postgres (bytea),
-- the same way clock-in selfies are stored (V13). Both columns are nullable so
-- a claim can still be submitted without a receipt. `receipt_photo_type` doubles
-- as the cheap "has a receipt" flag so lists never need to load the bytes.
-- (The legacy `receipt_url` string column is left in place, unused.)
ALTER TABLE claims
    ADD COLUMN receipt_photo BYTEA,
    ADD COLUMN receipt_photo_type VARCHAR(32);   -- e.g. 'image/jpeg'
