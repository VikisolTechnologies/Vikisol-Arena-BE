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
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        List<PostComment> comments = postCommentRepository.findTop200ByPostIdOrderByCreatedAtDesc(postId);
        Collections.reverse(comments); // most-recent-first from the query -> ascending for display
        Map<UUID, CandidateProfile> profiles = batchAuthorProfiles(comments);
        return comments.stream().map(c -> toResponse(c, profiles)).toList();
    }

    private Map<UUID, CandidateProfile> batchAuthorProfiles(List<PostComment> comments) {
        List<UUID> authorIds = comments.stream().map(c -> c.getAuthorUser().getId()).distinct().toList();
        if (authorIds.isEmpty()) return Map.of();
        return candidateProfileRepository.findByUserIdIn(authorIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
    }

    @Transactional
    public PostCommentResponse addComment(UUID userId, UUID postId, String content, UUID parentCommentId) {
        Post post = requirePost(postId);
        PostComment parent = null;
        if (parentCommentId != null) {
            parent = postCommentRepository.findById(parentCommentId)
                    .filter(c -> c.getPost().getId().equals(postId))
                    .orElseThrow(() -> new ResourceNotFoundException("That reply's comment isn't on this post"));
            if (parent.isDeleted()) {
                throw new BadRequestException("That comment was deleted - reply to the thread instead");
            }
        }
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) {
            throw new BadRequestException("You can't comment on this post");
        }
        User author = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        PostComment comment = postCommentRepository.save(PostComment.builder().post(post).authorUser(author).content(content).parentComment(parent).build());
        return toResponse(comment, batchAuthorProfiles(List.of(comment)));
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
        // Still has replies: keep the row as "[deleted]" so the replies under it keep their place.
        if (postCommentRepository.existsByParentCommentId(commentId)) {
            comment.setDeleted(true);
            comment.setContent("");
            return;
        }
        postCommentRepository.delete(comment);
    }

    private PostCommentResponse toResponse(PostComment comment, Map<UUID, CandidateProfile> profiles) {
        String parentId = comment.getParentComment() == null ? null : comment.getParentComment().getId().toString();
        if (comment.isDeleted()) {
            // Who wrote a deleted comment isn't shown either - only that something was here.
            return new PostCommentResponse(comment.getId().toString(), comment.getPost().getId().toString(),
                    null, "[deleted]", "", "", comment.getCreatedAt().toString(), parentId, true);
        }
        String name = comment.getAuthorUser().getName();
        String emoji = "🧑🏽";
        CandidateProfile profile = profiles.get(comment.getAuthorUser().getId());
        if (profile != null) {
            name = profile.getName();
            emoji = profile.getAvatarEmoji();
        }
        return new PostCommentResponse(comment.getId().toString(), comment.getPost().getId().toString(),
                comment.getAuthorUser().getId().toString(), name, emoji, comment.getContent(), comment.getCreatedAt().toString(),
                parentId, false);
    }

    private Post requirePost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
    }
}
