-- Round 5 (B): an employee can ask their employer to delete their account.
-- The request is recorded on the employee row so a repeat within 24h doesn't
-- re-notify the owner / HR admins. Null ⇒ no outstanding request; cleared when
-- an admin reactivates the account.
ALTER TABLE employees ADD COLUMN deletion_requested_at TIMESTAMPTZ;
