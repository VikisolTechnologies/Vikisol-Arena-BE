package com.vikisol.arena.projects.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A community project's details and outcome (ARENA-APP-FLOW §7, FE-API-GAPS row 26). The goal
// is the post's body; the title is the post's title.
@Entity
@Table(name = "arena_project_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectDetails extends BaseEntity {

    public enum Category { COMMUNITY, ENVIRONMENT, EDUCATION, TECH, DESIGN, ARTS, OTHER }

    public enum Where { LOCAL, REMOTE, BOTH }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "where_mode", length = 10)
    private Where where;

    private Integer weeks;

    @Column(length = 500)
    private String coverUrl;

    // PR6: what the project achieved, written by the owner when completing it.
    @Column(length = 1000)
    private String outcome;

    private Instant completedAt;
}
