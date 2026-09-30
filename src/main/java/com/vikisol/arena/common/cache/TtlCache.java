package com.vikisol.arena.common.cache;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One value, kept for a few seconds and shared by every request (PERFORMANCE.md). invalidate()
 * drops it at once, and a load that started before an invalidate() is returned to its caller
 * but never stored, so a write can't be hidden behind an older snapshot. A ttl of zero or less
 * turns caching off: every get() loads.
 */
public final class TtlCache<T> {

    private record Entry<T>(T value, long loadedAt, long generation) {
    }

    private final long ttlNanos;
    private final LongSupplier nanoClock;
    private final AtomicLong generation = new AtomicLong();
    private volatile Entry<T> entry;

    public TtlCache(java.time.Duration ttl) {
        this(ttl, System::nanoTime);
    }

    TtlCache(java.time.Duration ttl, LongSupplier nanoClock) {
        this.ttlNanos = ttl.toNanos();
        this.nanoClock = nanoClock;
    }

    public T get(Supplier<T> loader) {
        if (ttlNanos <= 0) return loader.get();
        long gen = generation.get();
        long now = nanoClock.getAsLong();
        Entry<T> e = entry;
        if (e != null && e.generation() == gen && now - e.loadedAt() < ttlNanos) return e.value();
        T value = loader.get();
        synchronized (this) {
            if (generation.get() == gen) entry = new Entry<>(value, now, gen);
        }
        return value;
    }

    public void invalidate() {
        synchronized (this) {
            generation.incrementAndGet();
            entry = null;
        }
    }
}
