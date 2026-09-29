package com.vikisol.arena.hiring.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.applications.entity.Application;

@Entity
@Table(name = "arena_application_answers", uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "question_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApplicationAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ScreeningQuestion question;

    @Column(nullable = false, length = 1000)
    private String answer;
}
