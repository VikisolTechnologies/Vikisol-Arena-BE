package com.vikisol.arena.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTest {

    @Test
    void splitsLowercasesAndDropsNoise() {
        assertThat(SearchText.terms("  React, Hyderabad!! a ")).containsExactly("react", "hyderabad");
        assertThat(SearchText.terms("C++ and C# roles")).containsExactly("c++", "and", "c#", "roles");
        assertThat(SearchText.terms(null)).isEmpty();
    }

    @Test
    void everyWordMustMatchSomewhere() {
        String rest = SearchText.haystack("Weekend game at Gachibowli", List.of("sports"));
        assertThat(SearchText.score(List.of("basket"), "Basketball 3v3", rest)).isPositive();
        assertThat(SearchText.score(List.of("basket", "gachibowli"), "Basketball 3v3", rest)).isPositive();
        assertThat(SearchText.score(List.of("basket", "kondapur"), "Basketball 3v3", rest)).isZero();
    }

    @Test
    void titleAndWholeWordHitsRankHigher() {
        int inTitle = SearchText.score(List.of("react"), "React developer", "");
        int partialTitle = SearchText.score(List.of("react"), "Reactive systems", "");
        int inBody = SearchText.score(List.of("react"), "Frontend developer", "uses react daily");
        assertThat(inTitle).isGreaterThan(partialTitle);
        assertThat(partialTitle).isGreaterThan(inBody);
    }
}
