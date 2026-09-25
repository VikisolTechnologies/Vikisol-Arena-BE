package com.vikisol.arena.platform.entity;

public enum ModerationContentType {
    JOB_POSTING, ROOM, POST, CONVERSATION;

    public String wireValue() {
        return name().toLowerCase();
    }
}
