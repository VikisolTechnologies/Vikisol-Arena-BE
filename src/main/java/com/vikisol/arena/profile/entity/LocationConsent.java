package com.vikisol.arena.profile.entity;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §5. PRECISE captures device geolocation (immediately
// geohash-encoded, the raw point never persisted - see DECISIONS.md); CITY uses a manually
// chosen home city's public center point; OFF stores nothing location-related at all and the
// app stays fully usable - Feed/Map both degrade to no distance-based centering/sort, never a
// blocked state.
public enum LocationConsent {
    PRECISE, CITY, OFF;

    public String wireValue() {
        return name().toLowerCase();
    }
}
