package com.vikisol.arena.projects.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// An open role on a community project (G29): "Illustrator, 1 spot".
@Entity
@Table(name = "arena_project_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private int slots = 1;

    // Row 26 (V36): helpful skills (JSON list) and weekly time.
    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String skillsJson = "[]";

    private Integer hoursPerWeek;
}
