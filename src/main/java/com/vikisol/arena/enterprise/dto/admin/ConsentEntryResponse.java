package com.vikisol.arena.enterprise.dto.admin;

public record ConsentEntryResponse(
        String candidateId,
        String candidateName,
        String unlockedAt,
        // Reflects the candidate's *current* consent, not what it was at unlock time - if they've
        // since withdrawn searchable-by-enterprises consent, this flips to false immediately (CA6/
        // G8: "consent withdrawal by a candidate revokes their unlocked contact from view").
        boolean stillConsenting
) {
}
