package com.vikisol.arena.rooms.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.dto.RoomMemberResponse;
import com.vikisol.arena.rooms.dto.RoomMessageResponse;
import com.vikisol.arena.rooms.dto.RoomResponse;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.entity.RoomMember;
import com.vikisol.arena.rooms.entity.RoomMemberRole;
import com.vikisol.arena.rooms.entity.RoomMessage;
import com.vikisol.arena.rooms.entity.RoomReport;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomMessageRepository;
import com.vikisol.arena.rooms.repository.RoomReportRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vikisol.arena.profile.entity.CandidateProfile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST polling, not WebSocket - zero WS/STOMP infrastructure exists anywhere in this codebase
 * (confirmed before writing this). Structurally an N-ary superset of messaging.ConversationService
 * (assertRoomMember mirrors assertParticipant exactly); Rooms only ever exist for ACTIVITY/ASK
 * posts, created lazily on the first approved join, never at post-creation time.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReportRepository roomReportRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ModerationService moderationService;
    private final ModerationItemRepository moderationItemRepository;
    private final NotificationService notificationService;
    private final PostRepository postRepository;

    @Transactional
    public Room getOrCreateForPost(Post post) {
        return roomRepository.findByPostId(post.getId()).orElseGet(() -> {
            Room room = roomRepository.save(Room.builder().post(post).build());
            roomMemberRepository.save(RoomMember.builder()
                    .room(room).user(post.getAuthorUser()).role(RoomMemberRole.ADMIN)
                    .lastReadAt(Instant.now()).build());
            return room;
        });
    }

    @Transactional
    public void addMember(Room room, User user) {
        if (roomMemberRepository.existsByRoomIdAndUserId(room.getId(), user.getId())) return;
        roomMemberRepository.save(RoomMember.builder()
                .room(room).user(user).role(RoomMemberRole.MEMBER).build());
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §3.4/§4: "Author is admin: ... remove"; "creator can
    // remove anyone" - previously entirely missing (safety-audit fix). Only the room's own
    // ADMIN (the post's author - getOrCreateForPost always seats them as ADMIN) can remove
    // someone, and only a MEMBER, never another ADMIN/themself (leaving your own post's room
    // isn't this endpoint's job). Reopens the post if removing a member drops it back under
    // capacity - the exact mirror of onJoinApproved's own spotsFilled/FULL bookkeeping in
    // PostService, kept here rather than routed through PostService for the same circular-
    // bean-dependency reason ModerationService.takedown() already documents.
    @Transactional
    public void removeMember(UUID actorUserId, UUID roomId, UUID targetUserId) {
        Room room = requireRoom(roomId);
        RoomMember actor = roomMemberRepository.findByRoomIdAndUserId(roomId, actorUserId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this room"));
        if (actor.getRole() != RoomMemberRole.ADMIN) {
            throw new AccessDeniedException("Only the room admin can remove members");
        }
        if (actorUserId.equals(targetUserId)) {
            throw new BadRequestException("Use leave, not remove, for yourself");
        }
        RoomMember target = roomMemberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("That person isn't in this room"));
        if (target.getRole() == RoomMemberRole.ADMIN) {
            throw new BadRequestException("Can't remove another admin");
        }
        roomMemberRepository.delete(target);

        Post post = room.getPost();
        if (post.getSpotsFilled() > 0) {
            post.setSpotsFilled(post.getSpotsFilled() - 1);
        }
        if (post.getStatus() == PostStatus.FULL) {
            post.setStatus(PostStatus.OPEN);
        }
        postRepository.save(post);

        notificationService.notifyRemovedFromRoom(target.getUser(), post);
    }

    @Transactional(readOnly = true)
    public Optional<String> findRoomIdForPost(UUID postId) {
        return roomRepository.findByPostId(postId).map(r -> r.getId().toString());
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(UUID userId) {
        return roomMemberRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(membership -> toResponse(membership.getRoom(), membership))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomMessageResponse> getMessages(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        assertRoomMember(userId, room);
        List<RoomMessage> messages = roomMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId);
        Collections.reverse(messages); // most-recent-first from the query -> ascending for display
        Map<UUID, CandidateProfile> profiles = batchSenderProfiles(messages.stream().map(m -> m.getSender().getId()));
        return messages.stream().map(m -> toResponse(m, userId, profiles)).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        assertRoomMember(userId, room);
        List<RoomMember> members = roomMemberRepository.findByRoomId(roomId);
        Map<UUID, CandidateProfile> profiles = batchSenderProfiles(members.stream().map(m -> m.getUser().getId()));
        return members.stream().map(m -> toResponse(m, profiles)).toList();
    }

    private Map<UUID, CandidateProfile> batchSenderProfiles(java.util.stream.Stream<UUID> userIds) {
        List<UUID> ids = userIds.distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return candidateProfileRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
    }

    @Transactional
    public RoomMessageResponse sendMessage(UUID userId, UUID roomId, String content) {
        Room room = requireRoom(roomId);
        assertRoomMember(userId, room);

        RoomMessage message = roomMessageRepository.save(RoomMessage.builder()
                .room(room).sender(requireUser(userId)).content(content).build());

        // No per-message push notification - unlike a 1:1 conversation, fanning this out to
        // every room member on every message would get noisy fast for group rooms. Unread state
        // surfaces via each member's own lastReadAt instead (same mechanism as the Messages
        // inbox's unread dot), touched only for the sender here; readers mark their own on open.
        RoomMember senderMembership = roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this room"));
        senderMembership.setLastReadAt(message.getCreatedAt());
        roomMemberRepository.save(senderMembership);

        // Single message, not a list - no batching win to be had, just look its one sender up.
        Map<UUID, CandidateProfile> profile = batchSenderProfiles(java.util.stream.Stream.of(userId));
        return toResponse(message, userId, profile);
    }

    @Transactional
    public void markRead(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this room"));
        membership.setLastReadAt(Instant.now());
        roomMemberRepository.save(membership);
    }

    @Transactional
    public void report(UUID userId, UUID roomId, String reason) {
        Room room = requireRoom(roomId);
        assertRoomMember(userId, room);
        User reporter = requireUser(userId);
        // RoomReport is the immutable raw-evidence record; fileRoomReport() is what actually
        // makes this actionable in the platform-admin queue - ARENA-V2-PRODUCT-ARCHITECTURE.md
        // §4's explicit "wired into the platform-admin moderation queue" requirement.
        roomReportRepository.save(RoomReport.builder().room(room).reporter(reporter).reason(reason).build());
        moderationService.fileRoomReport(room, reporter, reason);
    }

    @Transactional
    public void setMuted(UUID userId, UUID roomId, boolean muted) {
        Room room = requireRoom(roomId);
        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new AccessDeniedException("Not a member of this room"));
        membership.setMuted(muted);
        roomMemberRepository.save(membership);
    }

    // Called from PostService.cancel() - notifies every current room member (if a room even
    // exists yet; a post with zero approved joins never got one) that the activity was
    // cancelled by its author.
    @Transactional
    public void notifyRoomOfCancellation(Post post) {
        roomRepository.findByPostId(post.getId()).ifPresent(room -> {
            for (RoomMember member : roomMemberRepository.findByRoomId(room.getId())) {
                if (!member.getUser().getId().equals(post.getAuthorUser().getId())) {
                    notificationService.notifyPostCancelled(member.getUser(), post);
                }
            }
        });
    }

    // PostService.delete() - a post can only be hard-deleted (not just cancelled) if its room,
    // when one exists, has nothing worth preserving: no messages yet, and no moderation history.
    // A room that real people are actually talking in is never destroyed this way - the author
    // is pointed at cancel() instead (leaves the room and its history intact, same as
    // notifyRoomOfCancellation above), which is exactly the distinction this method exists to
    // enforce. Returns true when the post is now clear to delete (room removed, or never existed).
    @Transactional
    public boolean deleteRoomForPostIfEmpty(UUID postId) {
        Room room = roomRepository.findByPostId(postId).orElse(null);
        if (room == null) return true;
        if (roomMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId()).isPresent()) return false;
        if (moderationItemRepository.existsByRoomId(room.getId())) return false;
        roomMemberRepository.deleteByRoomId(room.getId());
        roomRepository.delete(room);
        return true;
    }

    private void assertRoomMember(UUID userId, Room room) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new AccessDeniedException("Not a member of this room");
        }
    }

    private RoomResponse toResponse(Room room, RoomMember membership) {
        // P3 audit fix: used to load the room's entire message history (findByRoomIdOrderBy...)
        // just to read the last element, and every member row just to count them - both once per
        // room in getMyRooms' loop. A single-row query and a COUNT query instead.
        RoomMessage last = roomMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId()).orElse(null);
        // Muted rooms never surface an unread badge, even with genuinely new messages - see
        // RoomMember.muted's own doc comment.
        boolean unread = !membership.isMuted() && last != null
                && (membership.getLastReadAt() == null || membership.getLastReadAt().isBefore(last.getCreatedAt()));
        int memberCount = (int) roomMemberRepository.countByRoomId(room.getId());
        Post post = room.getPost();
        return new RoomResponse(
                room.getId().toString(), post.getId().toString(), post.getBody(), post.getIntentType().wireValue(),
                memberCount, unread, membership.isMuted(), post.getStatus().wireValue(),
                last == null ? room.getCreatedAt().toString() : last.getCreatedAt().toString(),
                last == null ? null : truncate(last.getContent()));
    }

    private String truncate(String content) {
        return content.length() > 80 ? content.substring(0, 80) + "…" : content;
    }

    // P3 audit fix: was one candidateProfileRepository.findByUserId(...) call per message/member
    // - profiles is the batched IN-query result from batchSenderProfiles, looked up once for the
    // whole list rather than per row.
    private RoomMessageResponse toResponse(RoomMessage m, UUID viewingUserId, Map<UUID, CandidateProfile> profiles) {
        String senderName = m.getSender().getName();
        String senderEmoji = "🧑🏽";
        CandidateProfile profile = profiles.get(m.getSender().getId());
        if (profile != null) {
            senderName = profile.getName();
            senderEmoji = profile.getAvatarEmoji();
        }
        return new RoomMessageResponse(m.getId().toString(), m.getRoom().getId().toString(),
                m.getSender().getId().toString(), senderName, senderEmoji,
                m.getSender().getId().equals(viewingUserId), m.getContent(), m.getCreatedAt().toString());
    }

    private RoomMemberResponse toResponse(RoomMember member, Map<UUID, CandidateProfile> profiles) {
        String name = member.getUser().getName();
        String emoji = "🧑🏽";
        CandidateProfile profile = profiles.get(member.getUser().getId());
        if (profile != null) {
            name = profile.getName();
            emoji = profile.getAvatarEmoji();
        }
        return new RoomMemberResponse(member.getUser().getId().toString(), name, emoji, member.getRole().wireValue());
    }

    private Room requireRoom(UUID id) {
        return roomRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
