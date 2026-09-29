package com.vikisol.arena.common.policy;

import com.vikisol.arena.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedAttributesTest {

    @Test
    void catchesProtectedAttributesInAnyCase() {
        for (String text : List.of("What is your gender?", "Age 25-30 only", "How old are you?", "Religion?",
                "Which caste are you from", "Are you married?", "Women only", "Date of birth", "Marital status")) {
            assertThat(ProtectedAttributes.mentions(text)).as(text).isTrue();
        }
    }

    @Test
    void leavesOrdinaryWordsAlone() {
        for (String text : List.of("Can you manage a team?", "Average pace is fine", "Which language do you speak?",
                "Bring your own racket", "3 years of React", "Stage lights experience", "Human-centred design")) {
            assertThat(ProtectedAttributes.mentions(text)).as(text).isFalse();
        }
    }

    @Test
    void rejectNamesTheField() {
        assertThatThrownBy(() -> ProtectedAttributes.reject("the question", "Your age?"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageEndingWith("Please rephrase the question.");
    }
}
