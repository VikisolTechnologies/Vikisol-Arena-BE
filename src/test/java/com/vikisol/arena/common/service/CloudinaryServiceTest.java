package com.vikisol.arena.common.service;

import com.vikisol.arena.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudinaryServiceTest {

    private CloudinaryService service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudName", "demo");
        ReflectionTestUtils.setField(service, "apiKey", "key");
        ReflectionTestUtils.setField(service, "apiSecret", "abcd");
    }

    @Test
    void signMatchesCloudinaryDocsExample() {
        // Worked example from Cloudinary's "Generating authentication signatures" docs.
        Map<String, String> params = new TreeMap<>();
        params.put("timestamp", "1315060510");
        params.put("public_id", "sample_image");
        params.put("eager", "w_400,h_300,c_pad|w_260,h_200,c_crop");
        assertThat(service.sign(params)).isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
    }

    @Test
    void acceptsOwnFolderImagesAndVideos() {
        assertThatNoException().isThrownBy(() -> service.requireOwnMedia(List.of(
                "https://res.cloudinary.com/demo/image/upload/v1/arena/posts/abc.jpg",
                "https://res.cloudinary.com/demo/video/upload/v1/arena/posts/def.mp4")));
    }

    @Test
    void rejectsForeignOrOtherAccountUrls() {
        assertThatThrownBy(() -> service.requireOwnMedia(List.of("https://evil.example/pixel.gif")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.requireOwnMedia(List.of("https://res.cloudinary.com/other/image/upload/v1/arena/posts/a.jpg")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMoreThanFour() {
        String url = "https://res.cloudinary.com/demo/image/upload/v1/arena/posts/a.jpg";
        assertThatThrownBy(() -> service.requireOwnMedia(List.of(url, url, url, url, url)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unconfiguredStillAllowsPostsWithoutMedia() {
        ReflectionTestUtils.setField(service, "apiSecret", "");
        assertThatNoException().isThrownBy(() -> service.requireOwnMedia(List.of()));
        assertThatThrownBy(service::signUpload).isInstanceOf(BadRequestException.class);
    }

    // --- B12: local-disk fallback (Cloudinary unconfigured, 'local' profile active) ---

    private FileStorageService storage;

    private void goLocal() {
        ReflectionTestUtils.setField(service, "apiSecret", "");
        ReflectionTestUtils.setField(service, "storagePublicBaseUrl", "http://localhost:8081/api/v1/files");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        ReflectionTestUtils.setField(service, "environment", env);
        storage = mock(FileStorageService.class);
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
    }

    @Test
    void noLocalFallbackWithoutTheLocalProfile() {
        ReflectionTestUtils.setField(service, "apiSecret", "");
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment()); // no active profiles
        assertThat(service.isLocalFallbackActive()).isFalse();
        assertThatThrownBy(service::signUpload).isInstanceOf(BadRequestException.class);
    }

    @Test
    void localProfileWithCloudinaryStillConfiguredUsesCloudinary() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        ReflectionTestUtils.setField(service, "environment", env);
        assertThat(service.isLocalFallbackActive()).isFalse(); // isConfigured() wins
        assertThat(service.signUpload().cloudName()).isEqualTo("demo");
    }

    @Test
    void localFallbackSignsAndUploadsToLocalDisk() throws Exception {
        goLocal();
        var sig = service.signUpload();
        assertThat(sig.cloudName()).isEqualTo("local");
        assertThat(sig.uploadUrl()).endsWith("/media/local-upload");

        when(storage.store(any(), any(), any(), any()))
                .thenReturn(new FileStorageService.StoredFile("http://localhost:8081/api/v1/files/post-media/x/upload/a.jpg", "a.jpg", 10));
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String url = service.localUpload(file, sig.folder(), String.valueOf(sig.timestamp()), sig.signature());
        assertThat(url).isEqualTo("http://localhost:8081/api/v1/files/post-media/x/upload/a.jpg");
        assertThat(service.isLocalMediaUrl(url)).isTrue();
        assertThat(service.isLocalMediaUrl("https://res.cloudinary.com/demo/image/upload/v1/arena/posts/a.jpg")).isFalse();
    }

    @Test
    void localUploadRejectsATamperedSignature() throws Exception {
        goLocal();
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.localUpload(file, "post-media", "1700000000", "not-the-real-signature"))
                .isInstanceOf(BadRequestException.class);
    }

    // MARATHON-BE-2 step 1b item 3: a correctly-signed-but-old signature used to be valid forever -
    // a captured signature+file could be replayed at any later time. Now it expires like a real
    // Cloudinary signature does.
    @Test
    void localUploadRejectsAnExpiredButOtherwiseValidSignature() throws Exception {
        goLocal();
        Map<String, String> params = new TreeMap<>();
        params.put("folder", "post-media");
        params.put("timestamp", "1700000000"); // long past
        String signature = service.localSign(params);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.localUpload(file, "post-media", "1700000000", signature))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void localFallbackAcceptsOwnLocalUrlsAndRejectsOthers() {
        goLocal();
        assertThatNoException().isThrownBy(() -> service.requireOwnMedia(
                List.of("http://localhost:8081/api/v1/files/post-media/x/upload/a.jpg")));
        assertThatThrownBy(() -> service.requireOwnMedia(List.of("https://evil.example/pixel.gif")))
                .isInstanceOf(BadRequestException.class);
        // A real Cloudinary-shaped URL isn't accepted either while running the local fallback -
        // nothing uploaded through this instance could ever have produced one.
        assertThatThrownBy(() -> service.requireOwnMedia(
                List.of("https://res.cloudinary.com/demo/image/upload/v1/arena/posts/a.jpg")))
                .isInstanceOf(BadRequestException.class);
    }
}
