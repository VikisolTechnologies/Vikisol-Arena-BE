package com.vikisol.arena.communities.entity;

public enum CommunityRole {
    OWNER, MODERATOR, MEMBER;

    public String wireValue() {
        return name().toLowerCase();
    }

    public boolean canModerate() {
        return this == OWNER || this == MODERATOR;
    }
}
