package com.vikisol.arena.projects.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// The role someone asked for, and their note to the owner (G29). Whether they're in is the join
// request's status (arena_post_joins), so approval, rooms and leaving work as for any post.
@Entity
@Table(name = "arena_project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProjectMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private ProjectRole role;

    @Column(length = 500)
    private String message;
}
