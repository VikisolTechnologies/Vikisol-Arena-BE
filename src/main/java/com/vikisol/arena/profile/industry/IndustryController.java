package com.vikisol.arena.profile.industry;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// FE-API-GAPS row 62. GET /public/industries feeds the pickers (active only, no sign-in needed,
// covered by SecurityConfig's GET "/public/**"). /admin/industries is staff-only, behind the
// admin 2FA filter, and every change is audited.
@RestController
@RequiredArgsConstructor
public class IndustryController {

    private final IndustryCatalogue catalogue;

    public record PublicIndustry(String key, String label) {
    }

    public record AddIndustryRequest(@Size(max = 80, message = "must be at most 80 characters") String label, Integer position) {
    }

    public record UpdateIndustryRequest(@Size(max = 80, message = "must be at most 80 characters") String label,
                                        Boolean active, Integer position) {
    }

    @GetMapping("/public/industries")
    public ResponseEntity<ApiResponse<List<PublicIndustry>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(catalogue.active().stream()
                .map(r -> new PublicIndustry(r.key(), r.label())).toList()));
    }

    @GetMapping("/admin/industries")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<IndustryCatalogue.IndustryRow>>> adminList() {
        return ResponseEntity.ok(ApiResponse.ok(catalogue.all()));
    }

    @PostMapping("/admin/industries")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<IndustryCatalogue.IndustryRow>> add(
            @AuthenticationPrincipal UserPrincipal admin, @Valid @RequestBody AddIndustryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(catalogue.add(admin.getId(), request.label(), request.position())));
    }

    @PutMapping("/admin/industries/{key}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<IndustryCatalogue.IndustryRow>> update(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable String key, @Valid @RequestBody UpdateIndustryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(catalogue.update(admin.getId(), key, request.label(), request.active(), request.position())));
    }
}
