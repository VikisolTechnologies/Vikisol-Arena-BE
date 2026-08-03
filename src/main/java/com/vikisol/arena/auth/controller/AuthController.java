package com.vikisol.arena.auth.controller;

import com.vikisol.arena.auth.dto.AcceptInvitationRequest;
import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.auth.service.AuthService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.admin.InvitationPreviewResponse;
import com.vikisol.arena.enterprise.service.TeamService;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TeamService teamService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SessionResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account created", authService.signUp(request)));
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SessionResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.signIn(request)));
    }

    // Client-side-only concept for a stateless JWT (no server-side session store to clear) -
    // kept as a real endpoint so the frontend's signOut() call site has somewhere to hit.
    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signOut() {
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SessionResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return ResponseEntity.ok(ApiResponse.ok(authService.currentSession(user)));
    }

    // Unauthenticated (permitAll via SecurityConfig's "/auth/**" rule) - lets the accept-invite
    // page confirm which company/role someone's joining before they set a password.
    @GetMapping("/invitations/{token}")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> previewInvitation(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.previewInvitation(token)));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<ApiResponse<SessionResponse>> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        User user = teamService.acceptInvitation(request.token(), request.name(), request.password());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        String candidateId = null; // invited roles are never TALENT
        return ResponseEntity.ok(ApiResponse.ok("Account created",
                new SessionResponse(user.getRole().wireValue(), candidateId, user.getName(), user.getEmail(), token)));
    }
}
