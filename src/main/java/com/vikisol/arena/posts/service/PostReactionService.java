package com.vikisol.arena.posts.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostReaction;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostReactionService {

    private final PostReactionRepository postReactionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;

    @Transactional
    public void react(UUID userId, UUID postId) {
        vote(userId, postId, 1);
    }

    /**
     * Phase 2 (Discuss) up/down vote: 1 = up, -1 = down, 0 = clear. Idempotent - voting the
     * same way twice is a no-op, switching direction updates the one row. See V14.
     */
    @Transactional
    public void vote(UUID userId, UUID postId, int value) {
        if (value != -1 && value != 0 && value != 1) {
            throw new BadRequestException("A vote must be 1, -1 or 0");
        }
        var existing = postReactionRepository.findByPostIdAndUserId(postId, userId);
        if (value == 0) {
            existing.ifPresent(postReactionRepository::delete);
            return;
        }
        if (existing.isPresent()) {
            existing.get().setValue((short) value);
            return;
        }
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) {
            throw new BadRequestException("You can't react to this post");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        postReactionRepository.save(PostReaction.builder().post(post).user(user).value((short) value).build());
    }

    @Transactional
    public void unreact(UUID userId, UUID postId) {
        postReactionRepository.findByPostIdAndUserId(postId, userId).ifPresent(postReactionRepository::delete);
    }
}
