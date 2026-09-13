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
    public static final String MODERATION_DISMISSED = "moderation.dismissed";
    public static final String SUBSCRIPTION_ADJUSTED = "subscription.adjusted";
    public static final String FLAG_TOGGLED = "flag.toggled";
    public static final String CONSENT_CHANGED = "consent.changed";
    public static final String DATA_EXPORTED = "data.exported";
    public static final String ACCOUNT_DELETED = "account.deleted";
    // ARENA-FIX-EVERYTHING.md Phase 1 - distinct from ACCOUNT_DELETED (self-service) so the
    // audit trail can tell "a user erased themselves" apart from "a platform_admin erased this
    // account on someone else's behalf" - the actor is the admin here, not the erased account.
    public static final String ACCOUNT_ERASED_BY_ADMIN = "account.erased_by_admin";

    // M9 (audit/observability, PROJECT-PROGRESS.md milestone model): recorded by
    // AgentServiceTokenAuthenticationFilter for every request bearing a JennySol-forwarded
    // round-trip service token, whether it goes on to authenticate or not — the one place Arena
    // can distinguish "a human did this" (every other action in this file) from "an AI agent did
    // this on a user's behalf," per ADR-003's own re-derive-authorization-independently principle.
    public static final String AGENT_ACTION_AUTHORIZED = "agent.action.authorized";
    public static final String AGENT_ACTION_DENIED = "agent.action.denied";
}
