package com.vikisol.arena.platform.entity;

public enum ModerationContentType {
    JOB_POSTING, ROOM, POST;

    public String wireValue() {
        return name().toLowerCase();
    }
}
