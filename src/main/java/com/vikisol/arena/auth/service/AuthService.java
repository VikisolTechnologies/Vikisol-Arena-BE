package com.vikisol.arena.auth.service;

import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import com.vikisol.arena.security.service.TotpService;
import com.vikisol.arena.seed.SeedDataFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
        String candidateId = null;
        if (user.getRole() == Role.TALENT) {
            candidateId = candidateProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getId().toString())
                    .orElse(null);
        }
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
        String candidateId = null;
        if (user.getRole() == Role.TALENT) {
            candidateId = candidateProfileRepository.findByUserId(user.getId())
                    .map(p -> p.getId().toString())
                    .orElse(null);
        }
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
