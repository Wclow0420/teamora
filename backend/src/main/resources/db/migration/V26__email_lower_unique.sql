-- Round 6 (16): emails are case-insensitively unique. The app now stores them
-- trimmed + lowercase; this normalises existing rows and enforces it in the DB.
-- Fails loudly (rather than silently merging accounts) if two rows differ only by case.
DO $$
DECLARE dup TEXT;
BEGIN
    SELECT lower(trim(email)) INTO dup
    FROM employees
    GROUP BY lower(trim(email))
    HAVING count(*) > 1
    LIMIT 1;
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION 'V26: more than one employee has the email % (ignoring case). Merge or rename them, then restart.', dup;
    END IF;
END $$;

UPDATE employees SET email = lower(trim(email)) WHERE email <> lower(trim(email));

CREATE UNIQUE INDEX uq_employees_email_lower ON employees (lower(email));
