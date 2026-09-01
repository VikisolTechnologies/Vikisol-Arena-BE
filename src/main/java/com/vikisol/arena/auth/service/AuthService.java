package com.vikisol.arena.auth.service;

import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.entity.VerificationLevel;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.util.HandleGenerator;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.PhoneOtpProvider;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import com.vikisol.arena.security.service.TotpService;
import com.vikisol.arena.seed.SeedDataFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // Login lockout (checklist §1) - 5 bad passwords locks the account for 15 minutes.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    // 2FA is mandatory for these roles once enabled - see checklist §1 ("2FA mandatory for
    // internal platform admins and company admins"). Enrollment itself (setup/enable) is
    // available to any authenticated user; sign-in only branches into the MFA-pending flow if
    // the account actually has totpEnabled=true, so non-admin roles are never forced to enroll.
    private static final Set<Role> MFA_ELIGIBLE_ROLES = Set.of(Role.COMPANY_ADMIN, Role.PLATFORM_ADMIN);

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylistService tokenDenylistService;
    private final TotpService totpService;
    private final AuthenticationManager authenticationManager;
    private final SeedDataFactory seedDataFactory;
    private final EmailProvider emailProvider;
    private final PhoneOtpProvider phoneOtpProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    // application.yml's actual key is "app.frontend-url" (hyphenated) - a dotted
    // "app.frontend.url" here silently resolves to the fallback rather than erroring, which is
    // exactly what happened (see this fix's own commit message for how it was caught).
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 10;
    private static final long PASSWORD_RESET_TTL_MINUTES = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public SignInOutcome signUp(SignUpRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Role role = Role.fromWireValue(request.role());
        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(role)
                .handle(HandleGenerator.generate(request.name(), userRepository::existsByHandle))
                .build();
        user = userRepository.save(user);

        if (role == Role.TALENT) {
            CandidateProfile profile = seedDataFactory.blankCandidateProfile(user);
            candidateProfileRepository.save(profile);
        } else if (role == Role.COMPANY_ADMIN) {
            // The only enterprise-side role this public signup endpoint ever creates - it's a
            // new tenant's first user (see ARENA-ENTERPRISE-SUITE.md). RECRUITER/HIRING_MANAGER
            // only ever come from accepting an invitation to an existing tenant (see
            // MembershipController.acceptInvitation), never this path.
            EnterpriseProfile profile = seedDataFactory.blankEnterpriseProfile(user);
            profile = enterpriseProfileRepository.save(profile);
            membershipRepository.save(Membership.builder()
                    .user(user).tenant(profile).status(MembershipStatus.ACTIVE).joinedAt(user.getCreatedAt())
                    .build());
        } else {
            throw new BadRequestException("This account type can't be created directly - ask your admin for an invite.");
        }

        // Best-effort welcome email - a notification failure must never fail signup itself (mirrors
        // how HRLMS-BE's own EmailService.send* helpers catch and log rather than propagate). Runs
        // through NoopEmailProvider today since no RESEND_API_KEY is configured; see
        // integration/config/IntegrationProviderConfig.
        try {
            emailProvider.sendEmail(EmailMessage.to(user.getEmail(),
                    "Welcome to Vikisol Arena",
                    "<p>Hi " + user.getName() + ",</p><p>Your Vikisol Arena account is ready. "
                            + (role == Role.TALENT
                                    ? "Complete your profile to start getting matched to roles."
                                    : "Post your first job to start building your pipeline.")
                            + "</p><p>- The Vikisol Arena team</p>"));
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", user.getEmail(), e.getMessage());
        }

        // A brand-new account never has TOTP enabled yet, so signup always issues a full session
        // directly - no MFA branch possible here.
        return issueSession(user);
    }

    @Transactional
    public SignInOutcome signIn(SignInRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            long minutesLeft = Math.max(1, Duration.between(Instant.now(), user.getLockedUntil()).toMinutes());
            throw new BadRequestException("Too many failed attempts. Try again in " + minutesLeft + " minute(s).");
        }
        // DPDP right-to-erasure (ProfileController's DELETE /me) - a deleted account can never
        // sign in again, checked before authenticate() so this doesn't also count as/trigger a
        // failed-attempt lockout increment for what is really "this account no longer exists."
        if (user != null && user.getDeletedAt() != null) {
            throw new BadCredentialsException("Invalid email or password");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        } catch (BadCredentialsException ex) {
            if (user != null) recordFailedAttempt(user);
            throw new BadCredentialsException("Invalid email or password");
        }
        // user is guaranteed non-null past this point - authenticationManager would have thrown
        // BadCredentialsException (caught above) for an unknown email.
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        if (user.getRole().hasTenant()) {
            membershipRepository.findByUserId(user.getId()).ifPresent(m -> {
                if (m.getTenant().getStatus() == TenantStatus.SUSPENDED) {
                    throw new BadRequestException("This company's account has been suspended. Contact Vikisol support for help.");
                }
            });
        }

        if (user.isTotpEnabled() && MFA_ELIGIBLE_ROLES.contains(user.getRole())) {
            return new SignInOutcome.MfaRequired(jwtTokenProvider.generateMfaPendingToken(user.getId()));
        }
        return issueSession(user);
    }

    @Transactional
    public SignInOutcome verifyMfa(String pendingToken, String code) {
        if (!jwtTokenProvider.validateToken(pendingToken) || !jwtTokenProvider.isMfaPending(pendingToken)) {
            throw new BadCredentialsException("This verification step has expired - please sign in again");
        }
        UUID userId = jwtTokenProvider.getUserIdFromMfaPendingToken(pendingToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account not found"));
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BadCredentialsException("Incorrect verification code");
        }
        return issueSession(user);
    }

    // --- P3 follow-up: change password/email, phone sign-in/signup, Google sign-in ---

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        // passwordSet=false means this account never got a real user-chosen password (a Google
        // or phone signup) - its passwordHash column holds a random value nobody knows, so there
        // is no "current password" to check. Every other account must prove they know it first.
        if (user.isPasswordSet()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordSet(true);
        userRepository.save(user);
    }

    /** Returns a fresh session - the JWT subject is the email, so the token the caller is
     * currently using becomes stale (its subject no longer resolves) the instant this commits. */
    @Transactional
    public SessionResponse changeEmail(UUID userId, String newEmail, String currentPassword) {
        User user = requireUser(userId);
        if (user.isPasswordSet() && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new BadRequestException("Current password is incorrect");
        }
        String normalized = newEmail.toLowerCase();
        if (!normalized.equals(user.getEmail()) && userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new BadRequestException("An account with this email already exists");
        }
        user.setEmail(normalized);
        userRepository.save(user);
        return issueSession(user).session();
    }

    // --- Forgot password ---

    /** Always succeeds from the caller's point of view regardless of whether the email exists -
     * standard practice to avoid leaking which emails have accounts (user enumeration). */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getDeletedAt() != null) {
            return;
        }
        String rawToken = generateResetToken();
        user.setPasswordResetTokenHash(passwordEncoder.encode(rawToken));
        user.setPasswordResetExpiresAt(Instant.now().plus(PASSWORD_RESET_TTL_MINUTES, ChronoUnit.MINUTES));
        userRepository.save(user);

        String resetLink = frontendUrl + "/reset-password?email=" + java.net.URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                + "&token=" + rawToken;
        // Best-effort, same "a notification failure must never fail the calling flow" contract as
        // signUp's welcome email - a Resend outage shouldn't make forgotPassword() itself error.
        try {
            emailProvider.sendEmail(EmailMessage.to(user.getEmail(),
                    "Reset your Vikisol Arena password",
                    "<p>Hi " + user.getName() + ",</p><p>Someone (hopefully you) asked to reset your Vikisol Arena "
                            + "password. This link expires in " + PASSWORD_RESET_TTL_MINUTES + " minutes:</p>"
                            + "<p><a href=\"" + resetLink + "\">" + resetLink + "</a></p>"
                            + "<p>If this wasn't you, you can safely ignore this email - your password hasn't changed.</p>"
                            + "<p>- The Vikisol Arena team</p>"));
        } catch (Exception e) {
            log.warn("Password reset email failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getPasswordResetTokenHash() == null || user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(Instant.now())
                || !passwordEncoder.matches(token, user.getPasswordResetTokenHash())) {
            // Deliberately the same generic message whether the email doesn't exist, the token
            // never existed, or it's just expired/wrong - specifics here would help an attacker
            // narrow down which case they're in.
            throw new BadRequestException("This reset link is invalid or has expired - request a new one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordSet(true);
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // --- Phone sign-in (existing, already-verified accounts only) ---

    @Transactional
    public void requestPhoneSigninOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new BadRequestException("No account found for this phone number"));
        issueOtp(user, phoneNumber);
    }

    @Transactional
    public SignInOutcome verifyPhoneSigninOtp(String phoneNumber, String code) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new BadRequestException("No account found for this phone number"));
        // Same DPDP/tenant-suspension gates as password sign-in (AuthService.signIn) - phone
        // sign-in is an alternate credential for the exact same account, not a separate path
        // that should skip them.
        if (user.getDeletedAt() != null) {
            throw new BadCredentialsException("This account no longer exists");
        }
        consumeOtp(user, code);
        userRepository.save(user);
        if (user.getRole().hasTenant()) {
            membershipRepository.findByUserId(user.getId()).ifPresent(m -> {
                if (m.getTenant().getStatus() == TenantStatus.SUSPENDED) {
                    throw new BadRequestException("This company's account has been suspended. Contact Vikisol support for help.");
                }
            });
        }
        if (user.isTotpEnabled() && MFA_ELIGIBLE_ROLES.contains(user.getRole())) {
            return new SignInOutcome.MfaRequired(jwtTokenProvider.generateMfaPendingToken(user.getId()));
        }
        return issueSession(user);
    }

    // --- Phone signup (brand-new account, TALENT only - see PhoneSignupVerifyRequest's comment)
    // ---

    @Transactional
    public void requestPhoneSignupOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (user != null && user.isPhoneVerified()) {
            throw new BadRequestException("This phone number is already registered - sign in instead");
        }
        if (user == null) {
            // Pending, unverified shell account - handle/name are set once verifyPhoneSignupOtp
            // knows the real name; a random, never-communicated password hash satisfies the
            // NOT NULL passwordHash column without giving anyone a usable password (passwordSet
            // stays false until changePassword sets a real one).
            user = User.builder()
                    .email(placeholderEmail())
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .passwordSet(false)
                    .name("New user")
                    .role(Role.TALENT)
                    .phoneNumber(phoneNumber)
                    .build();
        }
        issueOtp(user, phoneNumber);
    }

    @Transactional
    public SignInOutcome verifyPhoneSignupOtp(String phoneNumber, String code, String name) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BadRequestException("Request a code first"));
        if (user.isPhoneVerified()) {
            throw new BadRequestException("This phone number is already registered - sign in instead");
        }
        consumeOtp(user, code);
        user.setName(name);
        user.setHandle(HandleGenerator.generate(name, userRepository::existsByHandle));
        if (!user.getVerificationLevel().atLeast(VerificationLevel.PHONE)) {
            user.setVerificationLevel(VerificationLevel.PHONE);
        }
        userRepository.save(user);
        candidateProfileRepository.save(seedDataFactory.blankCandidateProfile(user));
        return issueSession(user);
    }

    // --- Google sign-in/signup ---

    @Transactional
    public SignInOutcome signInWithGoogle(String idToken) {
        // Checked here, not left to GoogleIdTokenVerifier.verify()'s own IllegalStateException -
        // that exception has no dedicated GlobalExceptionHandler mapping, so it would fall
        // through to the generic RuntimeException handler and leak its message (which names the
        // env var) straight into a 400 response. Same error-contract class of bug fixed in
        // PostService's embedOrNull - a config-state detail should never reach the client.
        if (!googleIdTokenVerifier.isConfigured()) {
            throw new BadRequestException("Google sign-in isn't available right now");
        }
        GoogleIdTokenVerifier.Verified verified = googleIdTokenVerifier.verify(idToken);
        if (verified == null) {
            throw new BadCredentialsException("Could not verify this Google sign-in - please try again");
        }
        User user = userRepository.findByGoogleId(verified.googleId()).orElse(null);
        if (user == null) {
            // Google's own email_verified=true (already checked in GoogleIdTokenVerifier) is a
            // strong-enough signal to link into an existing password account with the same
            // email, rather than erroring "email already in use" - standard, expected behavior
            // for "Sign in with Google" across most consumer apps.
            user = userRepository.findByEmailIgnoreCase(verified.email()).orElse(null);
            if (user != null) {
                user.setGoogleId(verified.googleId());
            } else {
                user = User.builder()
                        .email(verified.email())
                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .passwordSet(false)
                        .name(verified.name())
                        .role(Role.TALENT)
                        .googleId(verified.googleId())
                        .handle(HandleGenerator.generate(verified.name(), userRepository::existsByHandle))
                        .build();
                user = userRepository.save(user);
                candidateProfileRepository.save(seedDataFactory.blankCandidateProfile(user));
                return issueSession(user);
            }
        }
        userRepository.save(user);
        if (user.getDeletedAt() != null) {
            throw new BadCredentialsException("This account no longer exists");
        }
        return issueSession(user);
    }

    private void issueOtp(User user, String phoneNumber) {
        String code = generateOtpCode();
        user.setPhoneNumber(phoneNumber);
        user.setPendingOtpHash(passwordEncoder.encode(code));
        user.setPendingOtpExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        userRepository.save(user);
        phoneOtpProvider.sendOtp(phoneNumber, code);
    }

    private void consumeOtp(User user, String code) {
        if (user.getPendingOtpHash() == null || user.getPendingOtpExpiresAt() == null) {
            throw new BadRequestException("No verification code is pending - request a new one");
        }
        if (user.getPendingOtpExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("That code has expired - request a new one");
        }
        if (!passwordEncoder.matches(code, user.getPendingOtpHash())) {
            throw new BadRequestException("That code doesn't match");
        }
        user.setPhoneVerified(true);
        user.setPendingOtpHash(null);
        user.setPendingOtpExpiresAt(null);
    }

    private String generateOtpCode() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt(max));
    }

    private String placeholderEmail() {
        // Never delivered anywhere - purely so the NOT NULL/unique email column stays satisfied
        // for an account whose real identity anchor is its phone number, not an email address.
        // The user can set a real email later (ChangeEmailRequest).
        return "phone-" + UUID.randomUUID() + "@users.arena.vikisol.in";
    }

    /** Rotates the presented refresh token and mints a fresh access token. Throws
     * BadCredentialsException (translated to 401) if the refresh token is invalid, expired, or a
     * detected reuse - callers should treat that as "the session is over," not retry. */
    @Transactional(readOnly = true)
    public RefreshResult refreshAccessToken(String refreshToken) {
        RefreshTokenService.Result rotated = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new BadCredentialsException("Account not found"));
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        return new RefreshResult(accessToken, rotated.token());
    }

    public void signOut(String refreshToken, String accessToken) {
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            tokenDenylistService.denylist(jwtTokenProvider.getJtiFromToken(accessToken), jwtTokenProvider.getExpiryFromToken(accessToken));
        }
    }

    @Transactional
    public TotpSetupResult setupTotp(UUID userId) {
        User user = requireUser(userId);
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);
        return new TotpSetupResult(secret, totpService.otpAuthUri(secret, user.getEmail(), "Vikisol Arena"));
    }

    @Transactional
    public void enableTotp(UUID userId, String code) {
        User user = requireUser(userId);
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("Call /auth/2fa/setup first");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BadRequestException("Incorrect verification code");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disableTotp(UUID userId, String code) {
        User user = requireUser(userId);
        if (!user.isTotpEnabled() || !totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new BadRequestException("Incorrect verification code");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public SessionResponse currentSession(User user) {
        // This is the account's own User.id, not the CandidateProfile PK - despite the field's
        // name, every frontend call site (people/[id], feed/[id], RoomsInbox, CommentThread)
        // reads it as "myUserId" to compare against author/sender ids that are themselves
        // User.id (posts, comments, room messages). It used to hold the CandidateProfile PK,
        // which silently broke every one of those self-identity checks - found auditing P3's
        // CandidateProfile.id/User.id mismatch item (see PublicCandidateProfileResponse's
        // matching fix in CandidateProfileService.getPublicProfile).
        String candidateId = user.getRole() == Role.TALENT ? user.getId().toString() : null;
        return SessionResponse.of(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), null);
    }

    private void recordFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        userRepository.save(user);
    }

    private SignInOutcome.Success issueSession(User user) {
        // See currentSession()'s comment - this is the User.id, not the CandidateProfile PK.
        String candidateId = user.getRole() == Role.TALENT ? user.getId().toString() : null;
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId());
        SessionResponse session = SessionResponse.of(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), accessToken);
        return new SignInOutcome.Success(session, refreshToken);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BadCredentialsException("Account not found"));
    }

    public sealed interface SignInOutcome {
        record Success(SessionResponse session, String refreshToken) implements SignInOutcome {
        }

        record MfaRequired(String pendingToken) implements SignInOutcome {
        }
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }

    public record TotpSetupResult(String secret, String otpAuthUri) {
    }
}
