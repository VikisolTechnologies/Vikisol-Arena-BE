package com.vikisol.arena.common.embedding;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md §7.3's "relevance/quality are embedding-based" (Phase C).
 * Same Noop-provider convention as {@code EmailProvider}/{@code WhatsAppProvider}/
 * {@code PhoneOtpProvider}: callers only ever depend on this interface, never a concrete
 * implementation - see {@link EmbeddingProviderConfig} for which one actually gets wired in.
 */
public interface EmbeddingProvider {
    float[] embed(String text);

    int dimensions();
}
