package com.vikisol.arena.common.embedding;

/**
 * Stores an embedding as a plain comma-joined TEXT column rather than a Postgres {@code vector}/
 * array column - sidesteps both a new `pgvector` extension (see DECISIONS.md's PostGIS-avoidance
 * precedent, same reasoning) and any Hibernate array-type-mapping version risk, for a value only
 * ever read back into Java and compared in Java anyway. Same "simple over clever, no new infra
 * dependency" call as {@code common.geo.GeohashUtil}.
 */
public final class EmbeddingUtil {

    private EmbeddingUtil() {}

    public static String encode(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    public static float[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        String[] parts = encoded.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) vector[i] = Float.parseFloat(parts[i]);
        return vector;
    }

    /** Returns 0 (neutral, not negative) when either vector is missing or a dimension mismatch
     * would make the dot product meaningless - see OpenAiEmbeddingProvider's own dimension-
     * mismatch caveat for when this can happen. */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
