package com.vikisol.arena.common.controller;

import com.vikisol.arena.common.service.FileSigningService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

// Serves files stored by LocalDiskFileStorageService. Stays unauthenticated at the Spring
// Security level (SecurityConfig permits GET /files/**) since a direct browser navigation or
// <img src> can't attach a Bearer token - but every URL handed out today (see
// CandidateProfileMapper) is signed and time-limited by FileSigningService, so a bare guessed
// or leaked path with no/expired/wrong signature is rejected. Previously this endpoint had no
// access control at all (found via the ARENA-SHIP-IT.md endpoint audit).
@RestController
@RequiredArgsConstructor
public class FileController {

    @Value("${app.storage.root-dir:./uploads}")
    private String rootDir;

    private final FileSigningService fileSigningService;

    @GetMapping("/files/{module}/{entityId}/{documentType}/{fileName}")
    public ResponseEntity<Resource> getFile(HttpServletRequest request,
                                             @PathVariable String module, @PathVariable String entityId,
                                             @PathVariable String documentType, @PathVariable String fileName,
                                             @RequestParam(required = false) Long exp, @RequestParam(required = false) String sig) {
        // Was manually reformatted as "/files/%s/.../%s" - missing the server.servlet.
        // context-path ("/api/v1") that FileSigningService.sign() actually signs over (it
        // extracts the path straight from the full public URL, which does include it). Every
        // signed file URL this app has ever handed out - CV/resume downloads, any future
        // profile-photo/document upload through this same FileStorageService - has been 403ing
        // regardless of a correct signature, since the two sides were hashing different strings.
        // request.getRequestURI() is the actual path Spring received (context-path included by
        // construction), so this can never drift from what sign() computed again.
        String servedPath = request.getRequestURI();
        if (exp == null || sig == null || !fileSigningService.verify(servedPath, exp, sig)) {
            return ResponseEntity.status(403).build();
        }
        Path path = Path.of(rootDir, module, entityId, documentType, fileName);
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
