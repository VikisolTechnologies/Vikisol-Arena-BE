package com.vikisol.arena.docs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeployChecklistProxyTest {

    @Test
    void proxyGoLiveRequirementsStayDocumentedWithoutSecretValues() throws Exception {
        String checklist = Files.readString(Path.of("docs/DEPLOY-CHECKLIST.md"));

        assertThat(checklist)
                .contains("ARENA_PROXY_SECRET")
                .contains("never in")
                .contains("code, logs, docs or chat")
                .contains("never given to users")
                .contains("Leave it unset for normal production")
                .contains("calls are same-origin")
                .contains("host-only on `arena.vikisol.in`")
                .doesNotContain("ARENA_PROXY_SECRET=");
    }
}
