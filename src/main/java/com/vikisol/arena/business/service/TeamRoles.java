package com.vikisol.arena.business.service;

import java.util.List;

// G28: what each company-team role can do, for the Team screen. Each line mirrors a real
// @PreAuthorize guard (named in the comment) so the screen never promises something the API
// refuses. Change both together.
public final class TeamRoles {

    public record RoleView(String role, String label, List<String> can) {
    }

    public static final List<RoleView> CATALOGUE = List.of(
            new RoleView("company_admin", "Company admin", List.of(
                    "post_and_manage_jobs",        // JobPostingController: RECRUITER, COMPANY_ADMIN
                    "review_candidates",           // ApplicantController, HiringController: RECRUITER, COMPANY_ADMIN
                    "search_and_unlock_talent",    // TalentSearchController: RECRUITER, COMPANY_ADMIN
                    "edit_company_profile",        // EnterpriseProfileController: RECRUITER, COMPANY_ADMIN
                    "assign_hiring_manager",       // InterviewController assign-hiring-manager: RECRUITER, COMPANY_ADMIN
                    "interview_feedback",          // InterviewController feedback: RECRUITER, COMPANY_ADMIN, HIRING_MANAGER
                    "manage_team",                 // CompanyAdminController /team: COMPANY_ADMIN
                    "billing",                     // CompanyAdminController /billing: COMPANY_ADMIN
                    "audit_log",                   // CompanyAdminController /audit: COMPANY_ADMIN
                    "consent_view",                // CompanyAdminController /consent: COMPANY_ADMIN
                    "business_verification")),     // BusinessController: COMPANY_ADMIN writes
            new RoleView("recruiter", "Recruiter", List.of(
                    "post_and_manage_jobs",
                    "review_candidates",
                    "search_and_unlock_talent",
                    "edit_company_profile",
                    "assign_hiring_manager",
                    "interview_feedback")),
            new RoleView("hiring_manager", "Hiring manager", List.of(
                    "assigned_interviews",         // InterviewController /mine: HIRING_MANAGER
                    "interview_feedback")));

    private TeamRoles() {
    }
}
