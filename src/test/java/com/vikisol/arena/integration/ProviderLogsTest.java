package com.vikisol.arena.integration;

import com.vikisol.arena.integration.provider.ProviderLogs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderLogsTest {

    @Test
    void masksWhatAProviderEchoesBack() {
        String raw = "{\"error\":\"invalid recipient asha.rao@example.com / +91 98765 43210, code 482913\","
                + " \"key\":\"re_9f8e7d6c5b4a39281706f5e4d3c2b1a0\", \"auth\":\"Bearer abc.def.ghi\", \"status\": 422}";
        String safe = ProviderLogs.redact(raw);

        assertThat(safe).doesNotContain("asha.rao", "example.com", "98765", "43210", "482913",
                "re_9f8e7d6c5b4a39281706f5e4d3c2b1a0", "abc.def.ghi");
        assertThat(safe).contains("[email]", "[number]", "[redacted]", "invalid recipient");
        // Short numbers (like a status code inside the body) stay readable.
        assertThat(safe).contains("422");
    }

    @Test
    void truncatesLongBodiesAndToleratesNull() {
        assertThat(ProviderLogs.redact("x ".repeat(1000))).hasSizeLessThanOrEqualTo(501);
        assertThat(ProviderLogs.redact(null)).isEmpty();
    }
}
