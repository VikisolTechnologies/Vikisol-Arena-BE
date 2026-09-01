package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

// role deliberately omitted - phone-first signup is scoped to TALENT only for now (the primary
// "sign up on your phone in 30 seconds" audience); COMPANY_ADMIN accounts still go through the
// full email+password signup, same as invited RECRUITER/HIRING_MANAGER accounts always have.
public record PhoneSignupVerifyRequest(
        @NotBlank(message = "is required") String phoneNumber,
        @NotBlank(message = "is required") String code,
        @NotBlank(message = "is required") String name
) {
}
