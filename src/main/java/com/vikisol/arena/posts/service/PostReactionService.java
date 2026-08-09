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
        if (postReactionRepository.existsByPostIdAndUserId(postId, userId)) return; // idempotent
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) {
            throw new BadRequestException("You can't react to this post");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        postReactionRepository.save(PostReaction.builder().post(post).user(user).build());
    }

    @Transactional
    public void unreact(UUID userId, UUID postId) {
        postReactionRepository.findByPostIdAndUserId(postId, userId).ifPresent(postReactionRepository::delete);
    }
}
