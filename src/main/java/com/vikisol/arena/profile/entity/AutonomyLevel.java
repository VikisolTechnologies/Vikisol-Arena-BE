package com.vikisol.arena.profile.entity;

import com.vikisol.arena.common.exception.BadRequestException;

// How much Jenny prepares for the person: MANUAL (only when asked) or SUPERVISED (suggests on its
// own). Either way every action waits for the person's approval (AgentService.decideAction).
// There is no autopilot level (DECISIONS.md, 30 Sep 2026; V42).
public enum AutonomyLevel {
    MANUAL, SUPERVISED;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static AutonomyLevel fromWireValue(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        if (v.equals("AUTOPILOT")) {
            throw new BadRequestException("Jenny doesn't act on its own: it prepares, and you approve each action.");
        }
        try {
            return AutonomyLevel.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("autonomy must be one of manual, supervised");
        }
    }
}
