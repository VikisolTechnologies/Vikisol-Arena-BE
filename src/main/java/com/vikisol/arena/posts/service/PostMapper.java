package com.vikisol.arena.posts.service;

import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostMapper {

    private final CandidateProfileRepository candidateProfileRepository;

    // Single-post convenience overload (one extra query) - list/feed call sites should use the
    // batched overload with a pre-fetched authorProfiles map instead, same split as
    // ProjectMapper's toResponse(Bid) vs toResponse(Bid, Map).
    public PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId) {
        var profile = candidateProfileRepository.findByUserId(post.getAuthorUser().getId());
        return toResponse(post, viewingUserId, myJoinStatus, roomId, profile.orElse(null));
    }

    public PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId,
                                    Map<UUID, CandidateProfile> authorProfiles) {
        return toResponse(post, viewingUserId, myJoinStatus, roomId, authorProfiles.get(post.getAuthorUser().getId()));
    }

    private PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId, CandidateProfile authorProfile) {
        String authorName = post.getAuthorUser().getName();
        String authorEmoji = "🧑🏽";
        if (authorProfile != null) {
            authorName = authorProfile.getName();
            authorEmoji = authorProfile.getAvatarEmoji();
        }
        boolean mine = viewingUserId != null && post.getAuthorUser().getId().equals(viewingUserId);
        return new PostResponse(
                post.getId().toString(), post.getAuthorUser().getId().toString(), authorName, authorEmoji,
                post.getIntentType().wireValue(), post.getBody(), post.getLocationText(),
                post.getAudience().wireValue(), post.getVisibility().wireValue(),
                post.getCapacity(), post.getSpotsFilled(), post.getStatus().wireValue(),
                post.getStartsAt() == null ? null : post.getStartsAt().toString(),
                post.getEndsAt() == null ? null : post.getEndsAt().toString(),
                post.getTags(), post.getMediaUrls(), post.isJoinable(),
                mine ? Boolean.TRUE : null, myJoinStatus, roomId, post.getCreatedAt().toString()
        );
    }

    public PostJoinRequestResponse toResponse(PostJoinRequest joinRequest) {
        var profile = candidateProfileRepository.findByUserId(joinRequest.getUser().getId());
        String userName = joinRequest.getUser().getName();
        String userEmoji = "🧑🏽";
        if (profile.isPresent()) {
            userName = profile.get().getName();
            userEmoji = profile.get().getAvatarEmoji();
        }
        return new PostJoinRequestResponse(
                joinRequest.getId().toString(), joinRequest.getPost().getId().toString(),
                joinRequest.getUser().getId().toString(), userName, userEmoji,
                joinRequest.getStatus().wireValue(), joinRequest.getCreatedAt().toString());
    }

    public List<PostJoinRequestResponse> toResponseList(List<PostJoinRequest> requests) {
        return requests.stream().map(this::toResponse).toList();
    }
}
