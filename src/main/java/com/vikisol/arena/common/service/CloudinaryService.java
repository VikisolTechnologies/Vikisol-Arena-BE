package com.vikisol.arena.common.service;

import com.vikisol.arena.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Post photos and videos go browser -> Cloudinary directly (a signed upload), never through this
 * server: videos are far past the 10MB multipart limit here, and Railway's disk isn't durable
 * across deploys anyway (see LocalDiskFileStorageService). This service only does the two things
 * that must stay server-side: signing an upload (the API secret never leaves the backend) and
 * checking that a media URL a client sends back really is one of ours.
 *
 * Unconfigured (no CLOUDINARY_* env vars) is a normal state, not a startup failure - signing
 * reports "not set up yet" and posts simply can't carry media until the keys are added.
 */
@Service
public class CloudinaryService {

    // Signed into the upload itself, so a client can't swap in another format - anything else
    // is rejected by Cloudinary before it's stored.
    private static final String ALLOWED_FORMATS = "jpg,jpeg,png,webp,gif,heic,mp4,mov,webm";
    private static final String FOLDER = "arena/posts";
    public static final int MAX_MEDIA_PER_POST = 4;

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    public boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    public record UploadSignature(String cloudName, String apiKey, long timestamp, String folder,
                                  String allowedFormats, String signature, String uploadUrl) {
    }

    public UploadSignature signUpload() {
        if (!isConfigured()) {
            throw new BadRequestException("Photo and video uploads aren't set up yet.");
        }
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> params = new TreeMap<>();
        params.put("allowed_formats", ALLOWED_FORMATS);
        params.put("folder", FOLDER);
        params.put("timestamp", Long.toString(timestamp));
        return new UploadSignature(cloudName, apiKey, timestamp, FOLDER, ALLOWED_FORMATS, sign(params),
                "https://api.cloudinary.com/v1_1/%s/auto/upload".formatted(cloudName));
    }

    /**
     * Rejects anything that isn't an image/video delivered from this account's own folder - a
     * post can't be used to embed arbitrary third-party URLs (tracking pixels, off-site content).
     */
    public void requireOwnMedia(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return;
        if (mediaUrls.size() > MAX_MEDIA_PER_POST) {
            throw new BadRequestException("A post can have at most " + MAX_MEDIA_PER_POST + " photos or videos.");
        }
        if (!isConfigured()) {
            throw new BadRequestException("Photo and video uploads aren't set up yet.");
        }
        String image = "https://res.cloudinary.com/%s/image/upload/".formatted(cloudName);
        String video = "https://res.cloudinary.com/%s/video/upload/".formatted(cloudName);
        for (String url : mediaUrls) {
            boolean ours = url != null && (url.startsWith(image) || url.startsWith(video)) && url.contains("/" + FOLDER + "/");
            if (!ours) {
                throw new BadRequestException("That photo or video wasn't uploaded through Arena - please attach it again.");
            }
        }
    }

    // Cloudinary's signing scheme: params sorted by key, joined as k=v with '&', API secret
    // appended, SHA-1, hex. Package-private for CloudinaryServiceTest (checked against the worked
    // example in Cloudinary's own docs).
    String sign(Map<String, String> sortedParams) {
        String toSign = sortedParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + apiSecret;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
