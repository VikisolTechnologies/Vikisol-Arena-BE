package com.vikisol.arena.hiring.service;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.policy.ProtectedAttributes;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.hiring.dto.HiringDtos.*;
import com.vikisol.arena.hiring.entity.*;
import com.vikisol.arena.hiring.repository.*;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Jobs & applications, evidence-first (G22-G26): must-haves, screening questions, the candidate's
// evidence against each must-have and the recruiter's private per-must-have assessment. Counts
// only ("N of M must-haves"), never a score, never a ranking.
@Service
@RequiredArgsConstructor
public class HiringService {

    static final int MAX_REQUIREMENTS = 10;
    static final int MAX_REQUIREMENT_LENGTH = 120;
    static final int MAX_QUESTIONS = 5;

    private final JobPostingRepository postingRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRequirementRepository requirementRepository;
    private final ScreeningQuestionRepository questionRepository;
    private final ApplicationAnswerRepository answerRepository;
    private final ApplicationEvidenceRepository evidenceRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;

    // --- G22 / G23: the posting's requirements and questions (employer) --------------------

    @Transactional
    public JobRequirementsView setRequirements(UUID employerId, UUID postingId, RequirementsRequest request) {
        JobPosting posting = requireOwnPosting(employerId, postingId);
        if (evidenceRepository.existsByRequirementPostingId(postingId)) {
            throw new BadRequestException("Candidates have already answered these must-haves, so they can't change now");
        }
        List<String> must = cleanRequirements(request.mustHaves(), "must-have");
        List<String> nice = cleanRequirements(request.niceToHaves() == null ? List.of() : request.niceToHaves(), "nice-to-have");
        requirementRepository.deleteByPostingId(postingId);
        requirementRepository.flush();
        saveRequirements(posting, JobRequirement.Kind.MUST, must);
        saveRequirements(posting, JobRequirement.Kind.NICE, nice);
        return requirementsView(postingId);
    }

    @Transactional
    public JobRequirementsView setScreening(UUID employerId, UUID postingId, List<QuestionInput> questions) {
        JobPosting posting = requireOwnPosting(employerId, postingId);
        if (questions.size() > MAX_QUESTIONS) throw new BadRequestException("You can ask at most " + MAX_QUESTIONS + " screening questions");
        if (answerRepository.existsByQuestionPostingId(postingId)) {
            throw new BadRequestException("Candidates have already answered these questions, so they can't change now");
        }
        questions.forEach(q -> ProtectedAttributes.reject("the question", q.text()));
        questionRepository.deleteByPostingId(postingId);
        questionRepository.flush();
        int position = 0;
        for (QuestionInput q : questions) {
            questionRepository.save(ScreeningQuestion.builder().posting(posting).position(position++)
                    .text(q.text().trim()).required(!Boolean.FALSE.equals(q.required())).build());
        }
        return requirementsView(postingId);
    }

    // Anyone signed in can read what a job asks for before applying.
    @Transactional(readOnly = true)
    public JobRequirementsView requirements(UUID postingId) {
        postingRepository.findById(postingId).orElseThrow(() -> new ResourceNotFoundException("Job not found: " + postingId));
        return requirementsView(postingId);
    }

    // --- G24: the candidate's answers and evidence ----------------------------------------

    @Transactional
    public CandidateScreeningView saveScreening(UUID userId, UUID applicationId, ScreeningAnswersRequest request) {
        Application application = requireOwnApplication(userId, applicationId);
        if (application.getStage() != ApplicationStage.APPLIED && application.getStage() != ApplicationStage.SCREENING) {
            throw new BadRequestException("Your application has moved on, so these answers are fixed now");
        }
        UUID postingId = application.getJobPosting().getId();
        Map<UUID, ScreeningQuestion> questions = questionRepository.findByPostingIdOrderByPositionAsc(postingId).stream()
                .collect(Collectors.toMap(ScreeningQuestion::getId, Function.identity()));
        Map<UUID, JobRequirement> requirements = requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId).stream()
                .collect(Collectors.toMap(JobRequirement::getId, Function.identity()));
        Map<UUID, ApplicationAnswer> answers = answerRepository.findByApplicationId(applicationId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));
        for (AnswerInput a : request.answers() == null ? List.<AnswerInput>of() : request.answers()) {
            ScreeningQuestion q = questions.get(a.questionId());
            if (q == null) throw new BadRequestException("One of the answers is for a question this job doesn't ask");
            String text = a.answer() == null ? "" : a.answer().trim();
            ApplicationAnswer row = answers.get(q.getId());
            if (text.isEmpty()) {
                if (row != null) answerRepository.delete(row);
                continue;
            }
            if (row == null) row = ApplicationAnswer.builder().application(application).question(q).build();
            row.setAnswer(text);
            answerRepository.save(row);
        }
        Map<UUID, ApplicationEvidence> evidence = evidenceRepository.findByApplicationId(applicationId).stream()
                .collect(Collectors.toMap(e -> e.getRequirement().getId(), Function.identity()));
        for (EvidenceInput e : request.evidence() == null ? List.<EvidenceInput>of() : request.evidence()) {
            JobRequirement req = requirements.get(e.requirementId());
            if (req == null) throw new BadRequestException("One of the evidence items is for a requirement this job doesn't have");
            String text = e.evidence() == null || e.evidence().isBlank() ? null : e.evidence().trim();
            ApplicationEvidence row = evidence.get(req.getId());
            if (row == null) row = ApplicationEvidence.builder().application(application).requirement(req).build();
            row.setCandidateEvidence(text);
            evidenceRepository.save(row);
        }
        return candidateView(application);
    }

    @Transactional(readOnly = true)
    public CandidateScreeningView candidateScreening(UUID userId, UUID applicationId) {
        return candidateView(requireOwnApplication(userId, applicationId));
    }

    // --- G25 / G26: the employer's side ------------------------------------------------------

    @Transactional(readOnly = true)
    public FunnelView funnel(UUID employerId, UUID postingId) {
        requireOwnPosting(employerId, postingId);
        Map<String, Long> stages = new LinkedHashMap<>();
        for (ApplicationStage s : ApplicationStage.values()) stages.put(s.wireValue(), 0L);
        long total = 0;
        for (var row : applicationRepository.countByStageForPosting(postingId)) {
            stages.put(row.getStage().wireValue(), row.getCnt());
            total += row.getCnt();
        }
        return new FunnelView(stages, total);
    }

    // The candidate list's "must-have evidence" column, one query per list, not per row.
    @Transactional(readOnly = true)
    public Page<ApplicantEvidenceRow> evidenceSummaries(UUID employerId, UUID postingId, Pageable pageable) {
        requireOwnPosting(employerId, postingId);
        Page<Application> page = applicationRepository.findByJobPostingId(postingId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "appliedAt")));
        List<JobRequirement> must = mustHaves(postingId);
        Map<UUID, List<ApplicationEvidence>> byApplication = evidenceRepository
                .findByApplicationIdIn(page.stream().map(Application::getId).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getApplication().getId()));
        return page.map(a -> new ApplicantEvidenceRow(a.getId().toString(), a.getCandidate().getId().toString(),
                a.getCandidate().getName(), a.getStage().wireValue(), summary(must, byApplication.getOrDefault(a.getId(), List.of()))));
    }

    @Transactional(readOnly = true)
    public ApplicantEvidenceView applicantEvidence(UUID employerId, UUID applicationId) {
        Application application = requireOwnApplicant(employerId, applicationId);
        return employerView(application);
    }

    @Transactional
    public ApplicantEvidenceView assess(UUID employerId, UUID applicationId, UUID requirementId, AssessmentRequest request) {
        Application application = requireOwnApplicant(employerId, applicationId);
        JobRequirement requirement = requirementRepository.findById(requirementId)
                .filter(r -> r.getPosting().getId().equals(application.getJobPosting().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found: " + requirementId));
        ApplicationEvidence.Assessment assessment;
        try {
            assessment = ApplicationEvidence.Assessment.valueOf(request.assessment().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("assessment must be one of met, partly, not_met, unclear");
        }
        ApplicationEvidence row = evidenceRepository.findByApplicationIdAndRequirementId(applicationId, requirementId)
                .orElseGet(() -> ApplicationEvidence.builder().application(application).requirement(requirement).build());
        row.setAssessment(assessment);
        row.setAssessmentNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        row.setAssessedBy(userRepository.getReferenceById(employerId));
        row.setAssessedAt(Instant.now());
        evidenceRepository.save(row);
        return employerView(application);
    }

    // --- views --------------------------------------------------------------------------------

    private JobRequirementsView requirementsView(UUID postingId) {
        List<JobRequirement> all = requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId);
        return new JobRequirementsView(
                all.stream().filter(r -> r.getKind() == JobRequirement.Kind.MUST).map(r -> new Item(r.getId().toString(), r.getText())).toList(),
                all.stream().filter(r -> r.getKind() == JobRequirement.Kind.NICE).map(r -> new Item(r.getId().toString(), r.getText())).toList(),
                questionRepository.findByPostingIdOrderByPositionAsc(postingId).stream()
                        .map(q -> new QuestionView(q.getId().toString(), q.getText(), q.isRequired())).toList());
    }

    private CandidateScreeningView candidateView(Application application) {
        UUID postingId = application.getJobPosting().getId();
        List<AnswerView> answers = answerViews(application);
        Map<UUID, ApplicationEvidence> evidence = evidenceRepository.findByApplicationId(application.getId()).stream()
                .collect(Collectors.toMap(e -> e.getRequirement().getId(), Function.identity()));
        List<EvidenceView> items = requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId).stream()
                .map(r -> new EvidenceView(r.getId().toString(), kind(r), r.getText(),
                        Optional.ofNullable(evidence.get(r.getId())).map(ApplicationEvidence::getCandidateEvidence).orElse(null)))
                .toList();
        int unanswered = (int) answers.stream().filter(a -> a.required() && a.answer() == null).count();
        return new CandidateScreeningView(answers, items, unanswered);
    }

    private ApplicantEvidenceView employerView(Application application) {
        UUID postingId = application.getJobPosting().getId();
        List<ApplicationEvidence> evidenceRows = evidenceRepository.findByApplicationId(application.getId());
        Map<UUID, ApplicationEvidence> evidence = evidenceRows.stream().collect(Collectors.toMap(e -> e.getRequirement().getId(), Function.identity()));
        List<ChecklistItem> checklist = requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId).stream().map(r -> {
            ApplicationEvidence e = evidence.get(r.getId());
            String shown = e == null ? null : e.getCandidateEvidence();
            return new ChecklistItem(r.getId().toString(), kind(r), r.getText(), shown,
                    shown == null ? "not_provided" : "candidate",
                    e == null || e.getAssessment() == null ? null : e.getAssessment().name().toLowerCase(Locale.ROOT),
                    e == null ? null : e.getAssessmentNote());
        }).toList();
        return new ApplicantEvidenceView(application.getId().toString(), application.getStage().wireValue(), checklist,
                answerViews(application), summary(mustHaves(postingId), evidenceRows));
    }

    private List<AnswerView> answerViews(Application application) {
        Map<UUID, String> given = answerRepository.findByApplicationId(application.getId()).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), ApplicationAnswer::getAnswer));
        return questionRepository.findByPostingIdOrderByPositionAsc(application.getJobPosting().getId()).stream()
                .map(q -> new AnswerView(q.getId().toString(), q.getText(), q.isRequired(), given.get(q.getId())))
                .toList();
    }

    // withEvidence: must-haves the candidate wrote evidence for. met: must-haves the team marked
    // "met". Both are counts out of the posting's must-haves.
    private static EvidenceSummary summary(List<JobRequirement> must, List<ApplicationEvidence> rows) {
        Set<UUID> mustIds = must.stream().map(JobRequirement::getId).collect(Collectors.toSet());
        int withEvidence = 0, met = 0;
        for (ApplicationEvidence e : rows) {
            if (!mustIds.contains(e.getRequirement().getId())) continue;
            if (e.getCandidateEvidence() != null) withEvidence++;
            if (e.getAssessment() == ApplicationEvidence.Assessment.MET) met++;
        }
        return new EvidenceSummary(must.size(), withEvidence, met);
    }

    private List<JobRequirement> mustHaves(UUID postingId) {
        return requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId).stream()
                .filter(r -> r.getKind() == JobRequirement.Kind.MUST).toList();
    }

    private static String kind(JobRequirement r) {
        return r.getKind() == JobRequirement.Kind.MUST ? "must" : "nice";
    }

    private void saveRequirements(JobPosting posting, JobRequirement.Kind kind, List<String> texts) {
        int position = 0;
        for (String text : texts) {
            requirementRepository.save(JobRequirement.builder().posting(posting).kind(kind).position(position++).text(text).build());
        }
    }

    private static List<String> cleanRequirements(List<String> raw, String label) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String r : raw) {
            String v = r == null ? "" : r.trim();
            if (v.isEmpty()) continue;
            if (v.length() > MAX_REQUIREMENT_LENGTH) throw new BadRequestException("Each " + label + " can be at most " + MAX_REQUIREMENT_LENGTH + " characters");
            ProtectedAttributes.reject("the " + label, v);
            out.putIfAbsent(v.toLowerCase(Locale.ROOT), v);
        }
        if (out.size() > MAX_REQUIREMENTS) throw new BadRequestException("You can list at most " + MAX_REQUIREMENTS + " " + label + "s");
        return List.copyOf(out.values());
    }

    // Tenant check, same rule as ApplicantService: anyone on the posting's company team.
    private JobPosting requireOwnPosting(UUID employerId, UUID postingId) {
        JobPosting posting = postingRepository.findById(postingId).orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(employerId);
        if (!posting.getEnterprise().getId().equals(tenant.getId())) throw new AccessDeniedException("Not your posting");
        return posting;
    }

    private Application requireOwnApplicant(UUID employerId, UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found: " + applicationId));
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(employerId);
        if (!application.getJobPosting().getEnterprise().getId().equals(tenant.getId())) throw new AccessDeniedException("Not your applicant");
        return application;
    }

    private Application requireOwnApplication(UUID userId, UUID applicationId) {
        CandidateProfile me = candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile for this account"));
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!application.getCandidate().getId().equals(me.getId())) throw new AccessDeniedException("Not your application");
        return application;
    }
}
