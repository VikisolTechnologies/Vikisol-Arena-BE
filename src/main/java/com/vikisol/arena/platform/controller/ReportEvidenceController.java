package com.vikisol.arena.platform.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.service.FileSigningService;
import com.vikisol.arena.common.service.FileStorageService;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

// Row 15: evidence for a report. Upload first (an image or a PDF, ≤10 MB), then send the returned
// url in `evidenceUrls` on POST /posts|rooms/{id}/report or /messages/conversations/{id}/report.
// Only the reporter's own uploads are accepted there; only moderators see them afterwards.
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReportEvidenceController {

    private static final Set<String> EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".pdf");

    private final FileStorageService fileStorageService;
    private final FileSigningService fileSigningService;

    @PostMapping(value = "/reports/evidence", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Evidence>> upload(@AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        String extension = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!EXTENSIONS.contains(extension)) {
            throw new com.vikisol.arena.common.exception.BadRequestException("Evidence must be an image (PNG, JPG, WebP) or a PDF");
        }
        var stored = fileStorageService.store(file, ModerationService.EVIDENCE_MODULE, principal.getId().toString(), "evidence");
        return ResponseEntity.ok(ApiResponse.ok(new Evidence(fileSigningService.sign(stored.url()), stored.fileName())));
    }

    public record Evidence(String url, String fileName) {
    }
}
