package com.vikisol.arena.platform.entity;

public enum ModerationContentType {
    JOB_POSTING, ROOM, POST, CONVERSATION,
    // FE-API-GAPS row 61 (V43): a report about a person, not a piece of content.
    USER;

    public String wireValue() {
        return name().toLowerCase();
    }
}
