package com.vikisol.arena.common.cache;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The feed's and trending's shared candidate windows (posts, jobs, projects), each kept for
 * app.feed.window-cache-seconds (default 5) - PERFORMANCE.md. Every write to a post, job or
 * project clears all of them once its transaction commits (the entity listener below), so a new,
 * closed or edited item shows on the next request. Only counts kept in other tables (comments,
 * reactions, reports, bids) can lag by up to the TTL, and for posts only in ranking: post
 * responses themselves are always read fresh.
 */
@Component
public class FeedWindowCache {

    private final Duration ttl;
    private final List<TtlCache<?>> caches = new CopyOnWriteArrayList<>();

    public FeedWindowCache(@Value("${app.feed.window-cache-seconds:5}") long seconds) {
        this.ttl = Duration.ofSeconds(seconds);
    }

    /** A new window, cleared together with all the others. */
    public <T> TtlCache<T> newWindow() {
        TtlCache<T> cache = new TtlCache<>(ttl);
        caches.add(cache);
        return cache;
    }

    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateNow();
                }
            });
        } else {
            invalidateNow();
        }
    }

    private void invalidateNow() {
        caches.forEach(TtlCache::invalidate);
    }

    /**
     * Registered on Post, JobPosting and Project; Spring Boot's Hibernate bean container injects
     * the cache. A provider, so JPA-only test slices without this bean still start.
     */
    public static class Listener {
        private final ObjectProvider<FeedWindowCache> windowCache;

        public Listener(ObjectProvider<FeedWindowCache> windowCache) {
            this.windowCache = windowCache;
        }

        @PostPersist
        @PostUpdate
        @PostRemove
        void changed(Object entity) {
            windowCache.ifAvailable(FeedWindowCache::invalidateAfterCommit);
        }
    }
}
