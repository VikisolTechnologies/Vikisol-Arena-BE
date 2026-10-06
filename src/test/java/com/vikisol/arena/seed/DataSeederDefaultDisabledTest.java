package com.vikisol.arena.seed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// B11 item 2: SEED_ENABLED must default to false, on every start, not just the first - an
// operator who forgets to set it at all must get a clean database. Two places decide the
// default and both must agree: the @ConditionalOnProperty on the bean itself (whether DataSeeder
// runs at all) and application.yml's ${SEED_ENABLED:...} placeholder (what app.seed.enabled
// actually resolves to when the env var is unset - a YAML default of "true" would silently
// override matchIfMissing=false, since the property would then never actually be "missing").
class DataSeederDefaultDisabledTest {

    @Test
    void theConditionalAnnotationDefaultsToDisabled() {
        ConditionalOnProperty annotation = DataSeeder.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.matchIfMissing()).isFalse();
        assertThat(annotation.value()).containsExactly("app.seed.enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
    }

    @Test
    void theYamlDefaultIsAlsoDisabled() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains("enabled: ${SEED_ENABLED:false}");
    }
}
