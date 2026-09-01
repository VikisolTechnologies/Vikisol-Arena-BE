-- Adds Google sign-in, phone-number sign-in/signup, and a real change-password flow (see
-- AuthService.signInWithGoogle/requestPhoneSignupOtp/requestPhoneSigninOtp/changePassword).
-- All additive - no existing column dropped or narrowed, no existing data touched beyond a
-- straight default backfill.

-- 1. password_set - distinguishes "the user actually knows a password for this account" from
--    "a random unusable hash was generated at creation so the NOT NULL passwordHash column is
--    satisfied" (Google/phone-only signups never get a real user-chosen password). Existing rows
--    all signed up the normal email+password way, so they default true; new Google/phone signups
--    explicitly set this false. changePassword() only demands the *current* password when this
--    is true - a passwordless account can set its first real password directly.
ALTER TABLE public.arena_users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT true;

-- 2. google_id - Google's stable per-account subject id ("sub" claim), the actual identity anchor
--    for Google sign-in (never the email alone - Google emails can theoretically be reused after
--    an account is deleted/renamed on their side, sub never is). Nullable + unique, same
--    multi-NULL-friendly pattern as `handle`/`phone_number` below - only rows that ever used
--    Google sign-in have a value.
ALTER TABLE public.arena_users ADD COLUMN google_id VARCHAR(255);
ALTER TABLE public.arena_users ADD CONSTRAINT uk_users_google_id UNIQUE (google_id);

-- 3. phone_number needs to be genuinely unique once it becomes a sign-in credential, not just a
--    self-attested verification-tier field - otherwise phone-based sign-in has no way to know
--    which account an OTP request/verify is even for. Existing rows are virtually all NULL (phone
--    verification has had no real SMS vendor wired yet - see BLOCKED.md), so this is expected to
--    be a no-op in practice; a real accidental duplicate here would mean two people had already
--    self-attested the exact same phone number, which the unique constraint is right to reject.
ALTER TABLE public.arena_users ADD CONSTRAINT uk_users_phone_number UNIQUE (phone_number);
