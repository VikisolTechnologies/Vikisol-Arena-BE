package com.vikisol.arena.communities.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommunitySlugTest {

    @Test
    void slugsAreLowercaseHyphenatedAscii() {
        assertThat(CommunityService.slugify("Hyderabad Foodies & Cafés!")).isEqualTo("hyderabad-foodies-cafes");
        assertThat(CommunityService.slugify("  React / Next.js  ")).isEqualTo("react-next-js");
        assertThat(CommunityService.slugify("హైదరాబాద్")).isEmpty(); // no Latin letters -> service generates one
        assertThat(CommunityService.slugify("a".repeat(60))).hasSize(40);
    }
}
