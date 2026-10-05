package com.vikisol.arena.platform.admin;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.platform.entity.ModerationItem;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * FE-API-GAPS rows 50 and 51: what an Arena admin can do to an account - warn, suspend (for a
 * number of days or until restored), ban, restore and force sign-out - and the account detail
 * they see. A suspension or ban also signs the person out everywhere. Every action is audited
 * with its reason. Staff accounts (platform admins) and the admin's own account are out of reach
 * here. Sessions are checked in AuthService (sign-in, refresh) and JwtAuthenticationFilter.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ModerationItemRepository moderationItemRepository;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    // Row 51. Never includes the password hash, 2FA secret, one-time codes or reset tokens.
    public record AccountDetail(String id, String name, String email, String handle, String role, String status,
                                String createdAt, String lastActiveAt, boolean twoFactorEnabled, boolean phoneVerified,
                                String verificationLevel, String suspendedUntil, String suspensionReason, String bannedAt,
                                String deletedAt, String lastDataExportAt, boolean deletionRequested,
                                String title, String location, boolean hasPhoto, long posts, long reportsAgainst,
                                long reportsFiled) {
    }

    @Transactional(readOnly = true)
    public AccountDetail detail(UUID userId) {
        User u = requireUser(userId);
        var profile = candidateProfileRepository.findByUserId(userId).orElse(null);
        long posts = count("select count(*) from arena_posts where author_user_id = ?", userId);
        long reportsFiled = count("select count(*) from arena_moderation_items where reporter_user_id = ?", userId);
        long reportsAgainst = count("""
                select count(*) from arena_moderation_items m
                left join arena_posts p on p.id = m.post_id
                left join arena_rooms r on r.id = m.room_id
                left join arena_posts rp on rp.id = r.post_id
                left join arena_conversations c on c.id = m.conversation_id
                where p.author_user_id = ? or rp.author_user_id = ? or m.reported_user_id = ?
                   or (m.conversation_id is not null and m.reporter_user_id <> ? and (c.user_a_id = ? or c.user_b_id = ?))
                """, userId, userId, userId, userId, userId, userId);
        return new AccountDetail(u.getId().toString(), u.getName(), u.getEmail(), u.getHandle(), u.getRole().wireValue(),
                status(u), u.getCreatedAt().toString(), str(u.getLastActiveAt()), u.isTotpEnabled(), u.isPhoneVerified(),
                u.getVerificationLevel().wireValue(), str(u.getSuspendedUntil()), u.getSuspensionReason(), str(u.getBannedAt()),
                str(u.getDeletedAt()), str(u.getLastDataExportAt()), u.getDeletedAt() != null,
                profile == null ? null : profile.getTitle(), profile == null ? null : profile.getLocation(),
                profile != null && profile.getPhotoUrl() != null,
                posts, reportsAgainst, reportsFiled);
    }

    /** active | suspended | banned | deleted */
    public static String status(User u) {
        if (u.getDeletedAt() != null) return "deleted";
        if (u.getBannedAt() != null) return "banned";
        if (u.isBlocked(Instant.now())) return "suspended";
        return "active";
    }

    @Transactional
    public AccountDetail suspend(UUID adminId, UUID userId, String reason, Integer durationDays) {
        User u = actionable(adminId, userId);
        Instant now = Instant.now();
        u.setSuspendedAt(now);
        u.setSuspendedUntil(durationDays == null ? null : now.plus(Duration.ofDays(durationDays)));
        u.setSuspensionReason(reason.trim());
        signOutEverywhere(u);
        userRepository.save(u);
        auditService.record(null, adminId, AuditActions.USER_SUSPENDED, target(u),
                reason.trim() + (durationDays == null ? " (until restored)" : " (" + durationDays + " days)"));
        return detail(userId);
    }

    @Transactional
    public AccountDetail ban(UUID adminId, UUID userId, String reason) {
        User u = actionable(adminId, userId);
        u.setBannedAt(Instant.now());
        u.setSuspensionReason(reason.trim());
        signOutEverywhere(u);
        userRepository.save(u);
        auditService.record(null, adminId, AuditActions.USER_BANNED, target(u), reason.trim());
        return detail(userId);
    }

    // Lifts a suspension or a ban.
    @Transactional
    public AccountDetail restore(UUID adminId, UUID userId, String reason) {
        User u = actionable(adminId, userId);
        if (!u.isBlocked(Instant.now()) && u.getSuspendedAt() == null) throw new BadRequestException("This account isn't suspended or banned");
        u.setSuspendedAt(null);
        u.setSuspendedUntil(null);
        u.setSuspensionReason(null);
        u.setBannedAt(null);
        userRepository.save(u);
        auditService.record(null, adminId, AuditActions.USER_RESTORED, target(u), reasonOrNone(reason));
        notificationService.notifySystem(u, NotificationService.SAFETY, "Your account is active again",
                "Arena's team has restored your account.");
        return detail(userId);
    }

    @Transactional
    public void warn(UUID adminId, UUID userId, String reason) {
        User u = actionable(adminId, userId);
        notificationService.notifySystem(u, NotificationService.SAFETY, "A note from Arena's team", reason.trim());
        auditService.record(null, adminId, AuditActions.USER_WARNED, target(u), reason.trim());
    }

    @Transactional
    public void forceSignOut(UUID adminId, UUID userId, String reason) {
        User u = actionable(adminId, userId);
        signOutEverywhere(u);
        userRepository.save(u);
        auditService.record(null, adminId, AuditActions.USER_SIGNED_OUT, target(u), reasonOrNone(reason));
    }

    // Row 50: the same actions from a report, on the account behind the reported content.
    @Transactional(readOnly = true)
    public UUID reportedUser(UUID moderationItemId) {
        ModerationItem item = moderationItemRepository.findById(moderationItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation item not found: " + moderationItemId));
        User target = switch (item.getContentType()) {
            case POST -> item.getPost().getAuthorUser();
            case ROOM -> item.getRoom().getPost().getAuthorUser();
            case JOB_POSTING -> item.getJobPosting().getEnterprise().getUser();
            case USER -> {
                if (item.getReportedUser() == null) throw new BadRequestException("That account no longer exists");
                yield item.getReportedUser();
            }
            case CONVERSATION -> {
                var c = item.getConversation();
                yield item.getReporter() != null && c.getUserA().getId().equals(item.getReporter().getId()) ? c.getUserB() : c.getUserA();
            }
        };
        return target.getId();
    }

    // Tokens issued before this second stop working; refresh tokens are revoked now.
    private void signOutEverywhere(User u) {
        u.setSessionsRevokedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        refreshTokenService.revokeAllForUser(u.getId());
    }

    private User actionable(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) throw new BadRequestException("You can't do this to your own account");
        User u = requireUser(userId);
        if (u.getRole() == Role.PLATFORM_ADMIN) throw new BadRequestException("Staff accounts are managed separately");
        if (u.getDeletedAt() != null) throw new BadRequestException("This account has been deleted");
        return u;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static String target(User u) {
        return u.getName() + " (" + u.getId() + ")";
    }

    private static String reasonOrNone(String reason) {
        return reason == null || reason.isBlank() ? "no reason given" : reason.trim();
    }

    private static String str(Instant i) {
        return i == null ? null : i.toString();
    }
}
