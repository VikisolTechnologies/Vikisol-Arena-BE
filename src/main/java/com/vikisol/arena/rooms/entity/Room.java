package com.vikisol.arena.rooms.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// 1:1 with Post, created lazily on the post's FIRST approved join (RoomService.getOrCreateForPost)
// - not at post-creation time. UPDATE posts never get one (not joinable); an ACTIVITY/ASK post
// with zero approved joiners doesn't need an empty row either.
@Entity
@Table(name = "arena_rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;
}
