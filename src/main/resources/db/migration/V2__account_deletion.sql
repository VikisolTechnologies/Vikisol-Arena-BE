-- DPDP right-to-erasure: a nullable timestamp is enough to gate future sign-in (see
-- AuthService.signIn()) without a destructive hard-delete cascading across every table that
-- references arena_users (applications, interviews, messages, audit events, credit ledger).
-- Existing rows default to NULL (never deleted), which is exactly correct.
ALTER TABLE arena_users ADD COLUMN deleted_at TIMESTAMP;
