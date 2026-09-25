package com.vikisol.arena.common.service;

import com.vikisol.arena.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
}
