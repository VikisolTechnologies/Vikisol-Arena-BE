package com.vikisol.arena.common.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// Hands a signed-in user a short-lived signature to upload post photos/videos straight to
// Cloudinary (see CloudinaryService for why uploads don't pass through this server). One
// signature covers every file in a single post - Cloudinary accepts it for an hour.
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MediaController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/upload-signature")
    public ResponseEntity<ApiResponse<CloudinaryService.UploadSignature>> uploadSignature() {
        return ResponseEntity.ok(ApiResponse.ok(cloudinaryService.signUpload()));
    }

    // B12, local dev only (CloudinaryService.isLocalFallbackActive): the signature from
    // POST /media/upload-signature points here instead of Cloudinary when the 'local' profile is
    // active and Cloudinary isn't configured. Same multipart fields and {"secure_url": "..."}
    // response shape as a real Cloudinary upload - arena-web's uploadMedia() (raw XHR, not
    // ApiResponse-wrapped) needs no changes to use either one. CloudinaryService.localUpload
    // itself refuses this outside the local fallback, so calling it directly in any other
    // environment is a no-op 400, not a hidden backdoor.
    @PostMapping("/local-upload")
    public ResponseEntity<Map<String, String>> localUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("signature") String signature) {
        return ResponseEntity.ok(Map.of("secure_url", cloudinaryService.localUpload(file, folder, timestamp, signature)));
    }
}
