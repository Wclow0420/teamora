-- When the owner finished the first-run setup wizard. NULL = setup not finished yet
-- (the admin dashboard offers "Finish setting up"). Existing companies predate the
-- wizard and are already set up, so they are back-filled as completed.
ALTER TABLE companies ADD COLUMN setup_completed_at TIMESTAMPTZ;
UPDATE companies SET setup_completed_at = now();
