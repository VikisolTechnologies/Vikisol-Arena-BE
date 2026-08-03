package com.vikisol.arena.auth.entity;

// Mirrors arena-web's `Role` type in types.ts. `ENTERPRISE` was retired in favor of
// RECRUITER/COMPANY_ADMIN/HIRING_MANAGER (see DECISIONS.md, ARENA-ENTERPRISE-SUITE.md) -
// every pre-existing ENTERPRISE row is migrated to COMPANY_ADMIN on startup
// (seed.RoleMigration), since every enterprise account so far is a sole tenant owner,
// which is exactly COMPANY_ADMIN's semantics.
public enum Role {
    TALENT, RECRUITER, COMPANY_ADMIN, HIRING_MANAGER, PLATFORM_ADMIN;

    public String wireValue() {
        return switch (this) {
            case TALENT -> "talent";
            case RECRUITER -> "recruiter";
            case COMPANY_ADMIN -> "company_admin";
            case HIRING_MANAGER -> "hiring_manager";
            case PLATFORM_ADMIN -> "platform_admin";
        };
    }

    public static Role fromWireValue(String value) {
        return switch (value) {
            case "talent" -> TALENT;
            case "recruiter" -> RECRUITER;
            case "company_admin" -> COMPANY_ADMIN;
            case "hiring_manager" -> HIRING_MANAGER;
            case "platform_admin" -> PLATFORM_ADMIN;
            // Enterprise signup always creates the tenant's first user, i.e. an admin.
            case "enterprise" -> COMPANY_ADMIN;
            default -> throw new IllegalArgumentException("Unknown role: " + value);
        };
    }

    public boolean isEnterpriseWorkspace() {
        return this == RECRUITER || this == COMPANY_ADMIN;
    }

    // Every enterprise-ish role belongs to exactly one tenant (Membership) - PLATFORM_ADMIN
    // deliberately does not (see DECISIONS.md), TALENT never has one either.
    public boolean hasTenant() {
        return this == RECRUITER || this == COMPANY_ADMIN || this == HIRING_MANAGER;
    }
}
