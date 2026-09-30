package com.vikisol.arena.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TtlCacheTest {

    private final AtomicLong now = new AtomicLong();
    private final AtomicInteger loads = new AtomicInteger();

    private Integer load() {
        return loads.incrementAndGet();
    }

    @Test
    void keepsTheValueUntilTheTtlRunsOut() {
        TtlCache<Integer> cache = new TtlCache<>(Duration.ofSeconds(5), now::get);
        assertThat(cache.get(this::load)).isEqualTo(1);
        now.addAndGet(Duration.ofSeconds(4).toNanos());
        assertThat(cache.get(this::load)).isEqualTo(1);
        now.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(cache.get(this::load)).isEqualTo(2);
    }

    @Test
    void invalidateDropsTheValueAtOnce() {
        TtlCache<Integer> cache = new TtlCache<>(Duration.ofSeconds(5), now::get);
        cache.get(this::load);
        cache.invalidate();
        assertThat(cache.get(this::load)).isEqualTo(2);
    }

    @Test
    void aLoadOverlappingAnInvalidateIsNotStored() {
        TtlCache<Integer> cache = new TtlCache<>(Duration.ofSeconds(5), now::get);
        // A write commits while this load is still reading: the caller gets the value, but the
        // next request reads again instead of seeing the older snapshot for five seconds.
        assertThat(cache.get(() -> {
            cache.invalidate();
            return load();
        })).isEqualTo(1);
        assertThat(cache.get(this::load)).isEqualTo(2);
    }

    @Test
    void zeroTtlTurnsCachingOff() {
        TtlCache<Integer> cache = new TtlCache<>(Duration.ZERO, now::get);
        cache.get(this::load);
        assertThat(cache.get(this::load)).isEqualTo(2);
    }
}
