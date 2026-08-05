package com.vikisol.arena.auth.controller;

import com.vikisol.arena.auth.dto.AcceptInvitationRequest;
import com.vikisol.arena.auth.dto.MfaVerifyRequest;
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
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SessionResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return ResponseEntity.ok(ApiResponse.ok(authService.currentSession(user)));
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
        String candidateId = null; // invited roles are never TALENT
        return ResponseEntity.ok(ApiResponse.ok("Account created",
                SessionResponse.of(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), accessToken)));
    }

    private ResponseEntity<ApiResponse<SessionResponse>> respond(AuthService.SignInOutcome outcome, HttpServletResponse response) {
        return switch (outcome) {
            case AuthService.SignInOutcome.Success success -> {
                refreshCookieHelper.set(response, success.refreshToken());
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
