package com.vikisol.arena.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSkill {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean verified;
}
