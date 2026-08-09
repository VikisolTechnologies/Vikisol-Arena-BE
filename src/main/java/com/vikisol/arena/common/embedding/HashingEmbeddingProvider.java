package com.vikisol.arena.common.embedding;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Always-active default {@link EmbeddingProvider} - the "Noop" of this pair, but (like
 * {@code NoopPhoneOtpProvider} doing a genuine OTP generate/hash/expire cycle and just skipping
 * the SMS send) a real, functioning technique rather than a stub: the hashing trick / feature
 * hashing used by production systems like Vowpal Wabbit and scikit-learn's
 * {@code HashingVectorizer} - hash each word into one of a fixed number of buckets, count
 * occurrences, L2-normalize. It's a genuine bag-of-words embedding with real (if word-overlap-
 * level, not deep-semantic) similarity behavior, computed with zero external dependency and zero
 * network call, so it's always on rather than a placeholder waiting for a "real" provider. See
 * DECISIONS.md for why this - not pgvector, not a paid API - is the honest Phase C default.
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSIONS = 128;
    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) return vector;

        for (String word : WORD_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (word.isBlank()) continue;
            int bucket = Math.floorMod(word.hashCode(), DIMENSIONS);
            vector[bucket] += 1f;
        }

        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
