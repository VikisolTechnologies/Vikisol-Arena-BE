package com.vikisol.arena.common.service;

import com.vikisol.arena.common.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Post photos and videos go browser -> Cloudinary directly (a signed upload), never through this
 * server: videos are far past the 10MB multipart limit here, and Railway's disk isn't durable
 * across deploys anyway (see LocalDiskFileStorageService). This service only does the two things
 * that must stay server-side: signing an upload (the API secret never leaves the backend) and
 * checking that a media URL a client sends back really is one of ours.
 *
 * Unconfigured (no CLOUDINARY_* env vars) is a normal state, not a startup failure - signing
 * reports "not set up yet" and posts simply can't carry media until the keys are added. The one
 * exception (B12): with the Spring profile {@code local} active and Cloudinary still
 * unconfigured, uploads fall back to local disk (via {@link FileStorageService}, same as every
 * other upload in this app) through {@code POST /media/local-upload} instead - a same-shaped
 * {@link UploadSignature}/{@code secure_url} response so arena-web's existing
 * {@code uploadMedia()} needs no changes to use it. Never active outside the {@code local}
 * profile - see {@link #warnIfUnconfiguredOutsideLocalProfile()}.
 */
@Service
@Slf4j
public class CloudinaryService {

    // Signed into the upload itself, so a client can't swap in another format - anything else
    // is rejected by Cloudinary before it's stored.
    private static final String ALLOWED_FORMATS = "jpg,jpeg,png,webp,gif,heic,mp4,mov,webm";
    private static final String FOLDER = "arena/posts";
    public static final int MAX_MEDIA_PER_POST = 4;

    // B12 local fallback only - images only (LocalDiskFileStorageService/MagicByteValidator have
    // no video support), never the real Cloudinary secret, never used outside local dev. Only
    // guards against a tampered folder/timestamp in the signature this server itself issued -
    // there's no third party here to keep a real secret from, unlike Cloudinary's.
    private static final String LOCAL_SECRET = "arena-local-dev-upload-not-a-real-secret";
    static final String LOCAL_MODULE = "post-media";

    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${app.storage.public-base-url:http://localhost:8081/api/v1/files}")
    private String storagePublicBaseUrl;

    @Autowired
    private FileStorageService fileStorageService;

    // Field-injected (not a constructor arg) so CloudinaryServiceTest's `new CloudinaryService()`
    // keeps working unchanged - this class has always been built that way with @Value fields set
    // via ReflectionTestUtils rather than a constructor.
    @Autowired(required = false)
    private Environment environment;

    public boolean isConfigured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /** B12: Cloudinary unconfigured, but the {@code local} Spring profile is active - serve
     * uploads from local disk instead of refusing them outright. */
    public boolean isLocalFallbackActive() {
        return !isConfigured() && environment != null && environment.matchesProfiles("local");
    }

    // ARENA-FE-VNEXT INBOX B12: "there, startup should warn loudly if Cloudinary is missing" -
    // "there" being anywhere that isn't local dev. A quiet "not set up yet" on the first upload
    // attempt is fine for local dev (see isLocalFallbackActive's fallback); on a real deployment
    // it means every photo/video upload across the whole app silently fails until someone notices
    // - worth a loud, impossible-to-miss startup line instead of waiting for a support ticket.
    @PostConstruct
    void warnIfUnconfiguredOutsideLocalProfile() {
        if (isConfigured()) return;
        if (isLocalFallbackActive()) {
            log.info("Cloudinary isn't configured, but the 'local' profile is active - photo/video "
                    + "uploads will use local disk storage (POST /media/local-upload) instead.");
            return;
        }
        log.warn("""
                ================================================================
                Cloudinary is NOT configured (CLOUDINARY_CLOUD_NAME / \
                CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET) and the 'local' \
                Spring profile is not active, so there is no local-disk \
                fallback either.
                Every photo/video upload (POST /media/upload-signature) will \
                fail with "Photo and video uploads aren't set up yet." until \
                Cloudinary is configured.
                ================================================================""");
    }

    public record UploadSignature(String cloudName, String apiKey, long timestamp, String folder,
                                  String allowedFormats, String signature, String uploadUrl) {
    }

    public UploadSignature signUpload() {
        if (isConfigured()) {
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> params = new TreeMap<>();
            params.put("allowed_formats", ALLOWED_FORMATS);
            params.put("folder", FOLDER);
            params.put("timestamp", Long.toString(timestamp));
            return new UploadSignature(cloudName, apiKey, timestamp, FOLDER, ALLOWED_FORMATS, sign(params),
                    "https://api.cloudinary.com/v1_1/%s/auto/upload".formatted(cloudName));
        }
        if (isLocalFallbackActive()) {
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> params = new TreeMap<>();
            params.put("folder", LOCAL_MODULE);
            params.put("timestamp", Long.toString(timestamp));
            String apiBase = storagePublicBaseUrl.replaceFirst("/files$", "");
            return new UploadSignature("local", "local", timestamp, LOCAL_MODULE, ALLOWED_FORMATS, localSign(params),
                    apiBase + "/media/local-upload");
        }
        throw new BadRequestException("Photo and video uploads aren't set up yet.");
    }

    // B12: the local-disk counterpart to a real Cloudinary upload - same multipart fields
    // arena-web's uploadMedia() already sends (folder/timestamp/signature; api_key and
    // allowed_formats are accepted but unused, there's no third party to present them to), same
    // {"secure_url": "..."} response shape. Returns the bare, unsigned file URL - signed fresh on
    // every read by PostMapper, never persisted signed (a signature baked in at upload time would
    // expire and the photo would 404 forever once FILE_SIGNED_URL_TTL_MS passes).
    // MARATHON-BE-2 step 1b item 3: the signature itself never expired - once valid, always valid,
    // so a captured signature could be replayed indefinitely. Same window Cloudinary itself uses
    // for its own signed uploads.
    private static final long SIGNATURE_TTL_SECONDS = 3600;

    public String localUpload(MultipartFile file, String folder, String timestamp, String signature) {
        if (!isLocalFallbackActive()) {
            throw new BadRequestException("Photo and video uploads aren't set up yet.");
        }
        Map<String, String> params = new TreeMap<>();
        params.put("folder", folder == null ? "" : folder);
        params.put("timestamp", timestamp == null ? "" : timestamp);
        if (signature == null || !MessageDigest.isEqual(
                localSign(params).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException("Invalid upload signature");
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid upload signature");
        }
        if (Math.abs(Instant.now().getEpochSecond() - issuedAt) > SIGNATURE_TTL_SECONDS) {
            throw new BadRequestException("This upload signature has expired - request a new one");
        }
        // A fresh id per upload (there's no post yet to scope this to - matches Cloudinary's own
        // flow, where media is uploaded before the post that will reference it exists).
        FileStorageService.StoredFile stored = fileStorageService.store(file, LOCAL_MODULE, UUID.randomUUID().toString(), "upload");
        return stored.url();
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
        if (isConfigured()) {
            String image = "https://res.cloudinary.com/%s/image/upload/".formatted(cloudName);
            String video = "https://res.cloudinary.com/%s/video/upload/".formatted(cloudName);
            for (String url : mediaUrls) {
                boolean ours = url != null && (url.startsWith(image) || url.startsWith(video)) && url.contains("/" + FOLDER + "/");
                if (!ours) {
                    throw new BadRequestException("That photo or video wasn't uploaded through Arena - please attach it again.");
                }
            }
            return;
        }
        if (isLocalFallbackActive()) {
            String prefix = storagePublicBaseUrl + "/" + LOCAL_MODULE + "/";
            for (String url : mediaUrls) {
                if (url == null || !url.startsWith(prefix)) {
                    throw new BadRequestException("That photo or video wasn't uploaded through Arena - please attach it again.");
                }
            }
            return;
        }
        throw new BadRequestException("Photo and video uploads aren't set up yet.");
    }

    /** True for a URL {@link #localUpload} could have produced - PostMapper signs these fresh on
     * every read, same as every other file URL this app hands out (see FileSigningService). A
     * real Cloudinary URL never matches (different host entirely) and is left untouched. */
    public boolean isLocalMediaUrl(String url) {
        return url != null && url.startsWith(storagePublicBaseUrl + "/" + LOCAL_MODULE + "/");
    }

    // Cloudinary's signing scheme: params sorted by key, joined as k=v with '&', API secret
    // appended, SHA-1, hex. Package-private for CloudinaryServiceTest (checked against the worked
    // example in Cloudinary's own docs).
    String sign(Map<String, String> sortedParams) {
        return hash(sortedParams, apiSecret);
    }

    String localSign(Map<String, String> sortedParams) {
        return hash(sortedParams, LOCAL_SECRET);
    }

    private static String hash(Map<String, String> sortedParams, String secret) {
        String toSign = sortedParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + secret;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
