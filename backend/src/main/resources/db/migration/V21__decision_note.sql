-- Optional reason an approver gives when declining a request. Shown to the
-- requester on their own list and appended to the "declined" notification.
-- Nullable: approvals and reason-less declines leave it empty.
ALTER TABLE leave_requests    ADD COLUMN decision_note VARCHAR(300);
ALTER TABLE claims            ADD COLUMN decision_note VARCHAR(300);
ALTER TABLE overtime_requests ADD COLUMN decision_note VARCHAR(300);
