-- Real forgot-password flow (AuthService.forgotPassword/resetPassword) - the one gap explicitly
-- flagged as scoped-out at the end of V9__auth_expansion.sql's pass, built now. Additive only.

ALTER TABLE public.arena_users ADD COLUMN password_reset_token_hash VARCHAR(255);
ALTER TABLE public.arena_users ADD COLUMN password_reset_expires_at TIMESTAMPTZ;
