package com.vikisol.arena.rooms.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.posts.entity.Post;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        return roomMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(m -> toResponse(m, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(UUID userId, UUID roomId) {
        Room room = requireRoom(roomId);
        assertRoomMember(userId, room);
        return roomMemberRepository.findByRoomId(roomId).stream().map(this::toResponse).toList();
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

        return toResponse(message, userId);
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
        roomReportRepository.save(RoomReport.builder()
                .room(room).reporter(requireUser(userId)).reason(reason).build());
    }

    private void assertRoomMember(UUID userId, Room room) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new AccessDeniedException("Not a member of this room");
        }
    }

    private RoomResponse toResponse(Room room, RoomMember membership) {
        List<RoomMessage> messages = roomMessageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());
        RoomMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        boolean unread = last != null && (membership.getLastReadAt() == null || membership.getLastReadAt().isBefore(last.getCreatedAt()));
        int memberCount = roomMemberRepository.findByRoomId(room.getId()).size();
        Post post = room.getPost();
        return new RoomResponse(
                room.getId().toString(), post.getId().toString(), post.getBody(), post.getIntentType().wireValue(),
                memberCount, unread,
                last == null ? room.getCreatedAt().toString() : last.getCreatedAt().toString(),
                last == null ? null : truncate(last.getContent()));
    }

    private String truncate(String content) {
        return content.length() > 80 ? content.substring(0, 80) + "…" : content;
    }

    private RoomMessageResponse toResponse(RoomMessage m, UUID viewingUserId) {
        String senderName = m.getSender().getName();
        String senderEmoji = "🧑🏽";
        var profile = candidateProfileRepository.findByUserId(m.getSender().getId());
        if (profile.isPresent()) {
            senderName = profile.get().getName();
            senderEmoji = profile.get().getAvatarEmoji();
        }
        return new RoomMessageResponse(m.getId().toString(), m.getRoom().getId().toString(),
                m.getSender().getId().toString(), senderName, senderEmoji,
                m.getSender().getId().equals(viewingUserId), m.getContent(), m.getCreatedAt().toString());
    }

    private RoomMemberResponse toResponse(RoomMember member) {
        String name = member.getUser().getName();
        String emoji = "🧑🏽";
        var profile = candidateProfileRepository.findByUserId(member.getUser().getId());
        if (profile.isPresent()) {
            name = profile.get().getName();
            emoji = profile.get().getAvatarEmoji();
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
