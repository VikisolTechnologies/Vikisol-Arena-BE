package com.vikisol.arena.auth.controller;

import com.vikisol.arena.auth.dto.AcceptInvitationRequest;
import com.vikisol.arena.auth.dto.ChangeEmailRequest;
import com.vikisol.arena.auth.dto.ChangePasswordRequest;
import com.vikisol.arena.auth.dto.ForgotPasswordRequest;
import com.vikisol.arena.auth.dto.GoogleSignInRequest;
import com.vikisol.arena.auth.dto.MfaVerifyRequest;
import com.vikisol.arena.auth.dto.PhoneOtpRequest;
import com.vikisol.arena.auth.dto.PhoneSigninVerifyRequest;
import com.vikisol.arena.auth.dto.PhoneSignupVerifyRequest;
import com.vikisol.arena.auth.dto.ResetPasswordRequest;
import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.dto.TotpCodeRequest;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.auth.service.AuthService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.admin.InvitationPreviewResponse;
import com.vikisol.arena.enterprise.service.TeamService;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshCookieHelper;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.SessionCookieHelper;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TeamService teamService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieHelper refreshCookieHelper;
    private final SessionCookieHelper sessionCookieHelper;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SessionResponse>> signUp(@Valid @RequestBody SignUpRequest request, HttpServletResponse response) {
        return respond(authService.signUp(request), response);
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SessionResponse>> signIn(@Valid @RequestBody SignInRequest request, HttpServletResponse response) {
        return respond(authService.signIn(request), response);
    }

    // Second step of the 2FA flow (see DECISIONS.md) - only reachable with the short-lived
    // pending token issued when signIn() found totpEnabled=true for an MFA-eligible role.
    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<SessionResponse>> verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletResponse response) {
        return respond(authService.verifyMfa(request.pendingToken(), request.code()), response);
    }

    // Rotates the refresh cookie and mints a new 15-min access token - see RefreshTokenService.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = refreshCookieHelper.read(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BadCredentialsException("No refresh token presented");
        }
        AuthService.RefreshResult result = authService.refreshAccessToken(refreshToken);
        refreshCookieHelper.set(response, result.refreshToken());
        sessionCookieHelper.set(response, result.accessToken());
        return ResponseEntity.ok(ApiResponse.ok(new RefreshTokenResponse(result.accessToken())));
    }

    // Revokes the refresh token server-side and denylists the still-live access token's jti -
    // genuinely ends the session, unlike the old client-discard-only stub.
    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signOut(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = refreshCookieHelper.read(request);
        String accessToken = extractBearerToken(request);
        authService.signOut(refreshToken, accessToken);
        refreshCookieHelper.clear(response);
        sessionCookieHelper.clear(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SessionResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return ResponseEntity.ok(ApiResponse.ok(authService.currentSession(user)));
    }

    // --- Account settings (authenticated) ---

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated", null));
    }

    // Issues a fresh session (see AuthService.changeEmail's own comment on why) - the caller's
    // current access token stops resolving the instant this commits, so the response must hand
    // back one that still works.
    @PostMapping("/change-email")
    public ResponseEntity<ApiResponse<SessionResponse>> changeEmail(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChangeEmailRequest request, HttpServletResponse response) {
        SessionResponse session = authService.changeEmail(principal.getId(), request.newEmail(), request.currentPassword());
        sessionCookieHelper.set(response, session.token());
        return ResponseEntity.ok(ApiResponse.ok("Email updated", session));
    }

    // --- Forgot password - public, see SecurityConfig ---

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Deliberately identical response whether or not the email exists (see AuthService.
        // forgotPassword's own comment) - the client always shows "check your email."
        return ResponseEntity.ok(ApiResponse.ok("If that email has an account, a reset link is on its way", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset - sign in with your new password", null));
    }

    // --- Phone sign-in (existing, already phone-verified accounts) - public, see SecurityConfig
    // ---

    @PostMapping("/phone/signin/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPhoneSigninOtp(@Valid @RequestBody PhoneOtpRequest request) {
        authService.requestPhoneSigninOtp(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok("Code sent", null));
    }

    @PostMapping("/phone/signin/verify-otp")
    public ResponseEntity<ApiResponse<SessionResponse>> verifyPhoneSigninOtp(@Valid @RequestBody PhoneSigninVerifyRequest request, HttpServletResponse response) {
        return respond(authService.verifyPhoneSigninOtp(request.phoneNumber(), request.code()), response);
    }

    // --- Phone signup (brand-new TALENT account) - public, see SecurityConfig ---

    @PostMapping("/phone/signup/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPhoneSignupOtp(@Valid @RequestBody PhoneOtpRequest request) {
        authService.requestPhoneSignupOtp(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok("Code sent", null));
    }

    @PostMapping("/phone/signup/verify-otp")
    public ResponseEntity<ApiResponse<SessionResponse>> verifyPhoneSignupOtp(@Valid @RequestBody PhoneSignupVerifyRequest request, HttpServletResponse response) {
        return respond(authService.verifyPhoneSignupOtp(request.phoneNumber(), request.code(), request.name()), response);
    }

    // --- Google sign-in/signup (find-or-create) - public, see SecurityConfig ---

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<SessionResponse>> signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request, HttpServletResponse response) {
        return respond(authService.signInWithGoogle(request.idToken()), response);
    }

    // --- 2FA enrollment (authenticated - any signed-in user can set this up for their own
    // account; only COMPANY_ADMIN/PLATFORM_ADMIN actually get gated on it at sign-in, see
    // AuthService.MFA_ELIGIBLE_ROLES) ---

    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<AuthService.TotpSetupResult>> setupTotp(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.setupTotp(principal.getId())));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiResponse<Void>> enableTotp(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody TotpCodeRequest request) {
        authService.enableTotp(principal.getId(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Two-factor authentication enabled", null));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<Void>> disableTotp(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody TotpCodeRequest request) {
        authService.disableTotp(principal.getId(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Two-factor authentication disabled", null));
    }

    // Unauthenticated (permitAll via SecurityConfig's "/auth/**" rule) - lets the accept-invite
    // page confirm which company/role someone's joining before they set a password.
    @GetMapping("/invitations/{token}")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> previewInvitation(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.previewInvitation(token)));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<ApiResponse<SessionResponse>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request, HttpServletResponse response) {
        User user = teamService.acceptInvitation(request.token(), request.name(), request.password());
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId());
        refreshCookieHelper.set(response, refreshToken);
        sessionCookieHelper.set(response, accessToken);
        String candidateId = null; // invited roles are never TALENT
        return ResponseEntity.ok(ApiResponse.ok("Account created",
                SessionResponse.of(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), accessToken)));
    }

    private ResponseEntity<ApiResponse<SessionResponse>> respond(AuthService.SignInOutcome outcome, HttpServletResponse response) {
        return switch (outcome) {
            case AuthService.SignInOutcome.Success success -> {
                refreshCookieHelper.set(response, success.refreshToken());
                sessionCookieHelper.set(response, success.session().token());
                yield ResponseEntity.ok(ApiResponse.ok(success.session()));
            }
            case AuthService.SignInOutcome.MfaRequired mfaRequired ->
                    ResponseEntity.ok(ApiResponse.ok(SessionResponse.mfaRequired(mfaRequired.pendingToken())));
        };
    }

    private String extractBearerToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    public record RefreshTokenResponse(String token) {
    }
}
