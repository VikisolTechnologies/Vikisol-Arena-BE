package com.vikisol.arena.posts.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.posts.dto.PostCommentResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostComment;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostCommentService {

    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final BlockService blockService;

    @Transactional(readOnly = true)
    public List<PostCommentResponse> getComments(UUID postId) {
        requirePost(postId);
        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PostCommentResponse addComment(UUID userId, UUID postId, String content) {
        Post post = requirePost(postId);
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) {
            throw new BadRequestException("You can't comment on this post");
        }
        User author = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        PostComment comment = postCommentRepository.save(PostComment.builder().post(post).authorUser(author).content(content).build());
        return toResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID userId, UUID postId, UUID commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (!comment.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("Comment not found: " + commentId);
        }
        boolean isAuthor = comment.getAuthorUser().getId().equals(userId);
        boolean isPostOwner = comment.getPost().getAuthorUser().getId().equals(userId);
        if (!isAuthor && !isPostOwner) {
            throw new AccessDeniedException("Not your comment");
        }
        postCommentRepository.delete(comment);
    }

    private PostCommentResponse toResponse(PostComment comment) {
        String name = comment.getAuthorUser().getName();
        String emoji = "🧑🏽";
        var profile = candidateProfileRepository.findByUserId(comment.getAuthorUser().getId());
        if (profile.isPresent()) {
            name = profile.get().getName();
            emoji = profile.get().getAvatarEmoji();
        }
        return new PostCommentResponse(comment.getId().toString(), comment.getPost().getId().toString(),
                comment.getAuthorUser().getId().toString(), name, emoji, comment.getContent(), comment.getCreatedAt().toString());
    }

    private Post requirePost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
    }
}
