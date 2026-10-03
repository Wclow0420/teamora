-- Round 6 (14): refresh tokens are stored as a SHA-256 hash, never in clear.
-- Existing tokens are hashed in place (same digest the app computes: lowercase hex
-- of SHA-256 over the UTF-8 token), so signed-in devices stay signed in.
ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;
UPDATE refresh_tokens SET token_hash = encode(sha256(convert_to(token_hash, 'UTF8')), 'hex');

-- When a token was revoked because it was exchanged for a new pair (rotation).
-- Replaying a rotated token shortly after (a retry after a lost response) is
-- tolerated; replaying any other revoked token is treated as theft and revokes
-- every session of that employee.
ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;
