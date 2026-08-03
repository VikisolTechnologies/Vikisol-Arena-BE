package com.vikisol.arena.audit;

// Namespaced string constants for AuditEvent.action - typo-safety when calling
// AuditService.record() without needing an enum migration for every new action type.
public final class AuditActions {
    private AuditActions() {}

    public static final String POSTING_CREATED = "posting.created";
    public static final String POSTING_CLOSED = "posting.closed";
    public static final String CANDIDATE_UNLOCKED = "candidate.unlocked";
    public static final String CREDIT_SPENT = "credit.spent";
    public static final String CREDIT_GRANTED = "credit.granted";
    public static final String STAGE_MOVED = "stage.moved";
    public static final String INTERVIEW_SCHEDULED = "interview.scheduled";
    public static final String FEEDBACK_SUBMITTED = "feedback.submitted";
    public static final String MESSAGE_SENT = "message.sent";
    public static final String MEMBER_INVITED = "member.invited";
    public static final String MEMBER_REMOVED = "member.removed";
    public static final String MEMBER_ROLE_CHANGED = "member.role_changed";
    public static final String PLAN_CHANGED = "plan.changed";
    public static final String TENANT_SUSPENDED = "tenant.suspended";
    public static final String TENANT_REACTIVATED = "tenant.reactivated";
    public static final String MODERATION_TAKEDOWN = "moderation.takedown";
}
