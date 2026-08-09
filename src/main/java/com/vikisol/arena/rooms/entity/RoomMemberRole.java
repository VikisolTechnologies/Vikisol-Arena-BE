package com.vikisol.arena.rooms.entity;

public enum RoomMemberRole {
    ADMIN, MEMBER;

    public String wireValue() {
        return name().toLowerCase();
    }
}
