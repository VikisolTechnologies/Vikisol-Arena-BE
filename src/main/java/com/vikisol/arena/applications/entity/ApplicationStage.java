package com.vikisol.arena.applications.entity;

// Mirrors arena-web's `ApplicationStage` type exactly.
public enum ApplicationStage {
    // HIRED (G25, V26) closes a successful pipeline; the rest are unchanged.
    // WITHDRAWN (V29): the candidate withdrew or declined an offer. Only the candidate sets it;
    // only the company sets the others.
    APPLIED, SCREENING, INTERVIEW, OFFER, HIRED, WITHDRAWN, REJECTED;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static ApplicationStage fromWireValue(String value) {
        return ApplicationStage.valueOf(value.trim().toUpperCase());
    }
}
