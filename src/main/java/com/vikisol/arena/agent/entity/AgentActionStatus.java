package com.vikisol.arena.agent.entity;

public enum AgentActionStatus {
    PENDING, DONE, DECLINED, FAILED, EXPIRED, UNKNOWN;

    public String wireValue() {
        return name().toLowerCase();
    }
}
