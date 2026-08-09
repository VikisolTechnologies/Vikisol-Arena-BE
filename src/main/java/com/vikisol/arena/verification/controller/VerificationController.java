package com.vikisol.arena.verification.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import com.vikisol.arena.verification.dto.ConfirmPhoneOtpRequest;
import com.vikisol.arena.verification.dto.RequestPhoneOtpRequest;
import com.vikisol.arena.verification.dto.SetDateOfBirthRequest;
import com.vikisol.arena.verification.dto.VerificationStatusResponse;
import com.vikisol.arena.verification.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/verification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.getStatus(principal.getId())));
    }

    @PostMapping("/phone/request")
    public ResponseEntity<ApiResponse<Void>> requestPhoneOtp(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody RequestPhoneOtpRequest request) {
        verificationService.requestPhoneOtp(principal.getId(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok("Code sent", null));
    }

    @PostMapping("/phone/confirm")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> confirmPhoneOtp(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ConfirmPhoneOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Phone verified", verificationService.confirmPhoneOtp(principal.getId(), request.code())));
    }

    @PutMapping("/date-of-birth")
    public ResponseEntity<ApiResponse<Void>> setDateOfBirth(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SetDateOfBirthRequest request) {
        verificationService.setDateOfBirth(principal.getId(), LocalDate.parse(request.dateOfBirth()));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
