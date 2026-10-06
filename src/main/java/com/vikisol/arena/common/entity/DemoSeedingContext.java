package com.vikisol.arena.common.entity;

/**
 * B11 item 2 (DataSeeder): every row the startup bootstrap seeder creates must carry
 * {@code demo_content=true}, the same flag {@code DemoContentService}'s on-demand seeder already
 * sets, so a stray seeded row is never indistinguishable from real user content. DataSeeder
 * builds ~30 different entity types across one long {@code run()} - tagging each builder call
 * site individually is exactly the kind of place a future addition quietly forgets the flag.
 * Instead, {@code DataSeeder} brackets its whole run with {@link #begin()}/{@link #end()}, and
 * {@link BaseEntity}'s {@code @PrePersist} hook consults this thread-local to tag every entity
 * inserted in between - correct by construction, not by remembering to call setDemoContent(true)
 * at each of ~30 call sites.
 */
public final class DemoSeedingContext {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private DemoSeedingContext() {
    }

    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void end() {
        ACTIVE.remove();
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }
}
