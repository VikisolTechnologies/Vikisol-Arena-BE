package com.vikisol.arena.common.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
