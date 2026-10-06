package com.vikisol.arena.common.controller;

import com.vikisol.arena.common.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// MARATHON-BE-2 step 1b item 3: B12's local-disk fallback for POST /media/upload-signature
// (CloudinaryService.isLocalFallbackActive) - split out of MediaController and gated with
// @Profile("local") so the endpoint doesn't exist at all outside local dev, rather than relying
// only on CloudinaryService.localUpload's own runtime check to refuse it.
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Profile("local")
public class LocalUploadController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/local-upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> localUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("signature") String signature) {
        return ResponseEntity.ok(Map.of("secure_url", cloudinaryService.localUpload(file, folder, timestamp, signature)));
    }
}
