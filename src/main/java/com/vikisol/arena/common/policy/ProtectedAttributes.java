package com.vikisol.arena.common.policy;

import com.vikisol.arena.common.exception.BadRequestException;

import java.util.Collection;
import java.util.regex.Pattern;

// Arena never asks for, stores or filters on gender, age, religion, caste or marital status
// (master context; the job form's protected-attributes notice). Free text that a host or
// recruiter writes for other people to answer or meet - host questions, must-haves, screening
// questions, activity details - is checked here. Matching is on whole words, so "manage",
// "average" or "language" never trip it.
public final class ProtectedAttributes {

    private static final Pattern PROTECTED = Pattern.compile(
            "\\b(genders?|sex|male|female|males|females|man|men|woman|women|boy|girl|"
                    + "age|aged|ages|how old|years old|date of birth|dob|birthday|born in|"
                    + "religions?|religious|caste|castes|marital|married|unmarried|single or married|"
                    + "husband|wife|spouse|pregnan\\w*|disabilit\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    public static final String MESSAGE =
            "Arena doesn't allow asking about or filtering by age, gender, marital status, religion, caste"
                    + " or disability. Please rephrase ";

    private ProtectedAttributes() {
    }

    public static boolean mentions(String text) {
        return text != null && PROTECTED.matcher(text).find();
    }

    // fieldLabel reads in the error: "... Please rephrase the question."
    public static void reject(String fieldLabel, String text) {
        if (mentions(text)) throw new BadRequestException(MESSAGE + fieldLabel + ".");
    }

    public static void rejectAny(String fieldLabel, Collection<String> texts) {
        if (texts != null) texts.forEach(t -> reject(fieldLabel, t));
    }
}
