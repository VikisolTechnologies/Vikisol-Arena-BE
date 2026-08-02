package com.vikisol.arena.auth.controller;

import com.vikisol.arena.auth.dto.SessionResponse;
import com.vikisol.arena.auth.dto.SignInRequest;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.auth.service.AuthService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
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
}
