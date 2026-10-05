package com.vikisol.arena.profile.service;

import com.vikisol.arena.profile.dto.PatchProfileRequest;
import com.vikisol.arena.profile.dto.ProfileBasicsResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.geo.CityCoordinates;
import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.common.service.FileStorageService;
import com.vikisol.arena.follows.dto.FollowCountsResponse;
import com.vikisol.arena.follows.service.FollowService;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.profile.dto.CandidateDataExport;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.LocationConsentRequest;
import com.vikisol.arena.profile.dto.PublicCandidateProfileResponse;
import com.vikisol.arena.profile.dto.SkillDto;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.LocationConsent;
import com.vikisol.arena.profile.entity.OpenTo;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final com.vikisol.arena.profile.industry.IndustryCatalogue industryCatalogue;
    private final com.vikisol.arena.privacy.PersonalDataService personalDataService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileMapper mapper;
    private final ScoringService scoringService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylistService tokenDenylistService;
    private final JwtTokenProvider jwtTokenProvider;
    private final FollowService followService;
    private final com.vikisol.arena.common.service.FileSigningService fileSigningService;
    private final ProfileVisibilityGuard visibilityGuard;
    private final com.vikisol.arena.auth.service.AccountTombstone accountTombstone;

    // FE-API-GAPS 1 and 5: the closed vocabularies the onboarding screens send.
    static final Set<String> INTENTS = Set.of("activities", "meet", "ask", "offer", "job", "hire", "projects", "explore");
    static final Set<String> AVAILABILITY = Set.of("weekdays", "weekends", "evenings");
    static final int MAX_INTERESTS = 20;
    static final int MAX_INTEREST_LENGTH = 30;
    private static final Set<String> PHOTO_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    @Transactional(readOnly = true)
    public CandidateProfile getEntityForUser(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile for this account"));
    }

    @Transactional(readOnly = true)
    public CandidateProfile getEntityById(UUID id) {
        return candidateProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getMyProfile(UUID userId) {
        return mapper.toResponse(getEntityForUser(userId));
    }

    @Transactional
    public CandidateProfileResponse updateDetails(UUID userId, String name, String title, String industry,
                                                    int experienceYears, int rateFloor, List<String> openTo,
                                                    Boolean cameForJob, String organization, Integer currentCtc,
                                                    Integer expectedCtc, String preferredLocation) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setName(name);
        profile.setTitle(title);
        profile.setIndustry(industryCatalogue.resolveForWrite(industry, profile.getIndustry()));
        profile.setExperienceYears(experienceYears);
        profile.setRateFloor(rateFloor);
        // Hibernate's @ElementCollection needs a mutable backing list to manage - Stream.toList()
        // returns an immutable one, which blows up with UnsupportedOperationException on flush.
        profile.setOpenTo(new java.util.ArrayList<>(openTo.stream().map(OpenTo::fromWireValue).toList()));
        // Job-intent branch fields - all optional, only overwritten when the caller actually
        // sends something. A null here means "not answered/skipped this time," not "clear the
        // previously saved value" - e.g. calling this again from the profile-edit screen for an
        // unrelated field shouldn't silently wipe a CTC the onboarding wizard already collected.
        if (cameForJob != null) profile.setCameForJob(cameForJob);
        if (organization != null) profile.setOrganization(organization);
        if (currentCtc != null) profile.setCurrentCtc(currentCtc);
        if (expectedCtc != null) profile.setExpectedCtc(expectedCtc);
        if (preferredLocation != null) profile.setPreferredLocation(preferredLocation);
        return saveAndScore(profile);
    }

    @Transactional
    public CandidateProfileResponse updateSkills(UUID userId, List<String> skillNames) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setSkills(new java.util.ArrayList<>(skillNames.stream().map(n -> new CandidateSkill(n, false)).toList()));
        return saveAndScore(profile);
    }

    // DPDP consent audit log (ARENA-SHIP-IT.md #5) - every change gets a timestamped AuditEvent
    // recording the before/after state, not just the silent overwrite this used to be. Consent
    // withdrawal ("searchableByEnterprises: true -> false") already takes effect immediately
    // wherever it's checked (see TalentSearchService.getConsentView()'s own comment) - this adds
    // the paper trail on top, it doesn't change the enforcement itself.
    @Transactional
    public CandidateProfileResponse updateConsent(UUID userId, ConsentDto consent) {
        CandidateProfile profile = getEntityForUser(userId);
        ConsentSettings before = profile.getConsent();
        profile.setConsent(new ConsentSettings(consent.autoApply(), consent.searchableByEnterprises()));
        CandidateProfileResponse response = saveAndScore(profile);
        auditService.record(null, userId, AuditActions.CONSENT_CHANGED, profile.getName(),
                "autoApply: %s -> %s; searchableByEnterprises: %s -> %s".formatted(
                        before.isAutoApply(), consent.autoApply(), before.isSearchableByEnterprises(), consent.searchableByEnterprises()));
        return response;
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §5. This is the ONLY place a raw device coordinate ever
    // touches this codebase, and it's discarded the instant it's encoded - only the resulting
    // geohash (and its own decoded-back-to-center approximation) is ever persisted. Switching
    // to OFF or CITY always clears any previously stored geohash/approxLat/approxLng, even if
    // the caller doesn't resend lat/lng - a consent downgrade must actually remove data, not
    // just stop collecting new data.
    @Transactional
    public CandidateProfileResponse updateLocationConsent(UUID userId, LocationConsentRequest request) {
        CandidateProfile profile = getEntityForUser(userId);
        LocationConsent consent = LocationConsent.valueOf(request.consent().trim().toUpperCase());
        profile.setLocationConsent(consent);
        profile.setGeohash(null);
        profile.setApproxLat(null);
        profile.setApproxLng(null);
        profile.setHomeCity(null);

        if (consent == LocationConsent.PRECISE && request.lat() != null && request.lng() != null) {
            if (request.lat() < -90 || request.lat() > 90 || request.lng() < -180 || request.lng() > 180) {
                throw new BadRequestException("Invalid coordinates");
            }
            String geohash = GeohashUtil.encode(request.lat(), request.lng());
            double[] approx = GeohashUtil.decode(geohash);
            profile.setGeohash(geohash);
            profile.setApproxLat(approx[0]);
            profile.setApproxLng(approx[1]);
        } else if (consent == LocationConsent.CITY && request.city() != null && !request.city().isBlank()) {
            profile.setHomeCity(request.city());
            CityCoordinates.lookup(request.city()).ifPresent(center -> {
                String geohash = GeohashUtil.encode(center[0], center[1]);
                double[] approx = GeohashUtil.decode(geohash);
                profile.setGeohash(geohash);
                profile.setApproxLat(approx[0]);
                profile.setApproxLng(approx[1]);
            });
        }
        return saveAndScore(profile);
    }

    // DPDP data export (ARENA-SHIP-IT.md #5) - the candidate's own profile fields plus their
    // application history, the personal data actually meaningful to a candidate requesting a
    // copy. Deliberately doesn't chase every FK reference across every module (messages,
    // interview notes, marketplace bids) - those are conversations/negotiations involving a
    // second party, not solely-owned personal data, and are a larger scope than this pass covers.
    // Not read-only: the DATA_EXPORTED audit row below is written in this transaction (a read-only
    // one never flushes it).
    @Transactional
    public CandidateDataExport exportMyData(UUID userId) {
        CandidateProfile profile = getEntityForUser(userId);
        User user = userRepository.findById(userId).orElseThrow();
        var applications = applicationRepository.findByCandidateId(profile.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .map(a -> new CandidateDataExport.ApplicationSummary(
                        a.getJobPosting() != null ? a.getJobPosting().getTitle() : null,
                        a.getStage().wireValue(), a.getAppliedAt().toString()))
                .toList();
        CandidateDataExport export = new CandidateDataExport(
                user.getEmail(), mapper.toResponse(profile), applications, Instant.now().toString(),
                personalDataService.export(userId));
        auditService.record(null, userId, AuditActions.DATA_EXPORTED, profile.getName());
        return export;
    }

    // DPDP right-to-erasure (ARENA-SHIP-IT.md #5) - anonymizes the profile and disables the
    // account rather than a hard delete: applications/interviews/messages/audit events all hold
    // FK references to this candidate, and cascading a real delete through every one of those
    // safely is a much larger, riskier change than this pass has budget for (see DECISIONS.md).
    // The practical DPDP-meaningful effect is the same either way - the candidate's identifying
    // info (name, bio, skills, CV) is gone and the account can never sign in again.
    @Transactional
    public void deleteMyAccount(UUID userId, String accessToken) {
        eraseAccount(userId, accessToken, "self-service erasure request");
    }

    // ARENA-FIX-EVERYTHING.md Phase 1 - PlatformUserService.eraseAccount's admin-triggered path
    // reuses this exact same logic (see its own comment for why), but needs its own audit reason
    // rather than inheriting "self-service erasure request" verbatim, which would be actively
    // wrong when an admin - not the account itself - triggered it. accessToken is always null
    // here: there's no request token of the admin's to denylist for THIS account, and none of the
    // target's either - revokeAllForUser below already ends every one of their live sessions.
    @Transactional
    public void eraseAccountAsAdmin(UUID userId) {
        eraseAccount(userId, null, "erased by a platform admin");
    }

    private void eraseAccount(UUID userId, String accessToken, String auditReason) {
        CandidateProfile profile = getEntityForUser(userId);
        User user = userRepository.findById(userId).orElseThrow();
        // Architect item 4 (legal): everything in the tables added for the new app - answers,
        // feedback, disputes, offers, applications' notes and assessments, requests, the career
        // layer - goes too. See PersonalDataService for the full list.
        personalDataService.erase(userId);

        if (profile.getPhotoUrl() != null) {
            fileStorageService.delete(profile.getPhotoUrl());
            profile.setPhotoUrl(null);
        }
        // The career layer (G18-G21) holds pay and job-seeking intent: erased outright, by
        // PersonalDataService above.
        profile.setIntents(new java.util.ArrayList<>());
        profile.setInterests(new java.util.ArrayList<>());
        profile.setAvailability(new java.util.ArrayList<>());
        if (profile.getCvUrl() != null) {
            fileStorageService.delete(profile.getCvUrl());
        }
        profile.setName("Deleted user");
        profile.setBio(null);
        profile.setSkills(new java.util.ArrayList<>());
        profile.setCvUrl(null);
        profile.setCvFileName(null);
        profile.setConsent(new ConsentSettings(false, false));
        candidateProfileRepository.save(profile);

        // ARENA-FIX-EVERYTHING.md Phase 1 fix - User.name is a separate field from
        // CandidateProfile.name (the latter is what most of the product actually renders, but
        // anything reading User.name directly - the platform_admin Users list, notably - kept
        // showing the real pre-erasure name forever. Erasure needs to mean erasure everywhere a
        // name is stored, not just the surface most screens happen to read from.
        user.setName("Deleted user");
        user.setDeletedAt(Instant.now());
        // ARCHITECT-REVIEW-BE-1 blocker #6: the real email/phone/handle/password hash used to
        // survive erasure untouched - the account's actual identity, not just its display name.
        accountTombstone.tombstone(user);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
        // Without this, the access token making THIS request stays valid for up to its
        // remaining 15min lifetime after "deletion" - long enough to call other endpoints and
        // partially un-anonymize what was just erased. Mirrors AuthService.signOut()'s denylist
        // call exactly.
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            tokenDenylistService.denylist(jwtTokenProvider.getJtiFromToken(accessToken), jwtTokenProvider.getExpiryFromToken(accessToken));
        }

        auditService.record(null, userId, AuditActions.ACCOUNT_DELETED, auditReason);
    }

    // --- FE-API-GAPS 1-5: onboarding basics -------------------------------------------------

    @Transactional(readOnly = true)
    public ProfileBasicsResponse getBasics(UUID userId) {
        return toBasics(getEntityForUser(userId));
    }

    // Gap 4 and 5: only the fields present in the request change; industry, experience and rate
    // are not required here (unlike PUT /profile/me/details).
    @Transactional
    public ProfileBasicsResponse patch(UUID userId, PatchProfileRequest request) {
        CandidateProfile profile = getEntityForUser(userId);
        if (request.name() != null) {
            if (request.name().isBlank()) throw new BadRequestException("Name can't be empty");
            profile.setName(request.name().trim());
            profile.getUser().setName(request.name().trim());
        }
        if (request.title() != null) profile.setTitle(request.title().trim());
        if (request.bio() != null) profile.setBio(request.bio().isBlank() ? null : request.bio().trim());
        if (request.availability() != null) {
            profile.setAvailability(new ArrayList<>(vocabulary(request.availability(), AVAILABILITY, "availability")));
        }
        if (request.interests() != null) setInterests(userId, request.interests());
        if (request.photoUrl() != null) {
            if (request.photoUrl().isBlank()) return deletePhoto(userId);
            String current = profile.getPhotoUrl() == null ? null : fileSigningService.sign(profile.getPhotoUrl());
            boolean same = profile.getPhotoUrl() != null && (request.photoUrl().equals(profile.getPhotoUrl())
                    || stripQuery(request.photoUrl()).equals(stripQuery(current)));
            if (!same) throw new BadRequestException("Upload a new photo with POST /profile/me/photo");
        }
        return toBasics(candidateProfileRepository.save(profile));
    }

    private static String stripQuery(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    @Transactional
    public ProfileBasicsResponse setIntents(UUID userId, List<String> intents) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setIntents(new ArrayList<>(vocabulary(intents, INTENTS, "intent")));
        return toBasics(candidateProfileRepository.save(profile));
    }

    @Transactional
    public ProfileBasicsResponse setInterests(UUID userId, List<String> interests) {
        CandidateProfile profile = getEntityForUser(userId);
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String raw : interests) {
            String interest = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (interest.isEmpty()) continue;
            if (interest.length() > MAX_INTEREST_LENGTH) {
                throw new BadRequestException("Each interest can be at most " + MAX_INTEREST_LENGTH + " characters");
            }
            unique.putIfAbsent(interest.toLowerCase(Locale.ROOT), interest);
        }
        if (unique.size() > MAX_INTERESTS) throw new BadRequestException("You can add up to " + MAX_INTERESTS + " interests");
        profile.setInterests(new ArrayList<>(unique.values()));
        return toBasics(candidateProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public String visibility(UUID userId) {
        return getEntityForUser(userId).getProfileVisibility().name().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public String setVisibility(UUID userId, String value) {
        CandidateProfile.ProfileVisibility v;
        try {
            v = CandidateProfile.ProfileVisibility.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("profile must be one of nearby, everyone, hidden");
        }
        CandidateProfile profile = getEntityForUser(userId);
        profile.setProfileVisibility(v);
        candidateProfileRepository.save(profile);
        return v.name().toLowerCase(Locale.ROOT);
    }

    // Gap 3. Images only; the old photo file is removed.
    @Transactional
    public ProfileBasicsResponse uploadPhoto(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        String extension = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!PHOTO_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("A profile photo must be a PNG, JPG or WebP image");
        }
        CandidateProfile profile = getEntityForUser(userId);
        FileStorageService.StoredFile stored = fileStorageService.store(file, "profile-photo", profile.getId().toString(), "photo");
        if (profile.getPhotoUrl() != null) fileStorageService.delete(profile.getPhotoUrl());
        profile.setPhotoUrl(stored.url());
        return toBasics(candidateProfileRepository.save(profile));
    }

    @Transactional
    public ProfileBasicsResponse deletePhoto(UUID userId) {
        CandidateProfile profile = getEntityForUser(userId);
        if (profile.getPhotoUrl() != null) {
            fileStorageService.delete(profile.getPhotoUrl());
            profile.setPhotoUrl(null);
        }
        return toBasics(candidateProfileRepository.save(profile));
    }

    // API-ISSUES.md: this used to always store/return the lowercased form, so a value the FE sent
    // in its own display casing (e.g. "Weekends", AVAILABILITY's Title Case) came back lowercased
    // ("weekends") from GET /profile/me/basics - a value read from the server wouldn't
    // exact-match the FE's own vocabulary constant and the right chip wouldn't highlight.
    // Validated case-insensitively against `allowed` (still lowercase, the wire/DB form), but
    // the caller's own casing and whitespace-trimmed spelling is what's stored and echoed back.
    private static List<String> vocabulary(List<String> values, Set<String> allowed, String label) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>(); // lowercase key -> first-seen casing
        for (String raw : values) {
            String trimmed = raw == null ? "" : raw.trim();
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (!allowed.contains(key)) {
                throw new BadRequestException("'" + raw + "' is not a valid " + label + ". Use one of: "
                        + allowed.stream().sorted().collect(Collectors.joining(", ")));
            }
            out.putIfAbsent(key, trimmed);
        }
        return List.copyOf(out.values());
    }

    private ProfileBasicsResponse toBasics(CandidateProfile p) {
        return new ProfileBasicsResponse(p.getName(), p.getTitle(), p.getBio(), fileSigningService.sign(p.getPhotoUrl()),
                List.copyOf(p.getIntents()), List.copyOf(p.getInterests()), List.copyOf(p.getAvailability()));
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C profile revamp - the public/other-user view
    // `/identity` never had at all (see DECISIONS.md). The canonical key is the PROFILE OWNER's
    // user id, since that's what Follow/Post already key on everywhere else. Talent Universe
    // search, applicants and shortlists hand out the CandidateProfile id instead, so a link built
    // from those 404'd here - the profile id is accepted as a fallback. The response id is
    // always the user id either way.
    @Transactional(readOnly = true)
    public PublicCandidateProfileResponse getPublicProfile(UUID userOrProfileId, UUID viewingUserId) {
        CandidateProfile profile = candidateProfileRepository.findByUserId(userOrProfileId)
                .or(() -> candidateProfileRepository.findById(userOrProfileId))
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));
        User user = profile.getUser();
        UUID targetUserId = user.getId();
        visibilityGuard.requireVisibleTo(viewingUserId, targetUserId);
        FollowCountsResponse counts = followService.getCounts(targetUserId, viewingUserId);
        String homeCity = profile.getLocationConsent() == LocationConsent.OFF ? null : profile.getHomeCity();
        return new PublicCandidateProfileResponse(
                targetUserId.toString(), profile.getName(), profile.getAvatarEmoji(), profile.getTitle(),
                profile.getIndustry().wireValue(), profile.getLocation(), profile.isRemote(),
                profile.getSkills().stream().map(s -> new SkillDto(s.getName(), s.isVerified())).toList(),
                profile.getExperienceYears(), profile.getOpenTo().stream().map(o -> o.wireValue()).toList(),
                profile.getCareerHealth(), profile.getBio(),
                user.getVerificationLevel().wireValue(), user.isPhoneVerified(), homeCity,
                counts.followerCount(), counts.followingCount(), counts.viewerFollows(),
                fileSigningService.sign(profile.getPhotoUrl()), List.copyOf(profile.getInterests()),
                List.copyOf(profile.getAvailability()));
    }

    // A person's user id from either their user id or their profile id (the ids GET /profile/{id} takes).
    @Transactional(readOnly = true)
    public UUID resolveUserId(UUID userOrProfileId) {
        if (userRepository.existsById(userOrProfileId)) return userOrProfileId;
        return candidateProfileRepository.findById(userOrProfileId).map(p -> p.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));
    }

    // FE-API-GAPS row 54 (with row 18's setting): the owner always sees their profile. Anyone
    // else gets the same 404 as for a missing profile when it is hidden, when it is "nearby" and
    // they aren't signed in, when either of them blocked the other, or when the account was
    // deleted or banned - so the answer never reveals that the person is on Arena. The actual
    // check now lives in ProfileVisibilityGuard, shared with every other per-person endpoint
    // (ARCHITECT-REVIEW-BE-1 blocker #2) - this used to be a private copy of it, here alone.

    @Transactional
    public CandidateProfileResponse updateAutonomy(UUID userId, AutonomyLevel autonomy) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setAutonomy(autonomy);
        return saveAndScore(profile);
    }

    @Transactional
    public CandidateProfileResponse uploadCv(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        CandidateProfile profile = getEntityForUser(userId);
        FileStorageService.StoredFile stored = fileStorageService.store(file, "candidate-cv", profile.getId().toString(), "cv");
        profile.setCvUrl(stored.url());
        profile.setCvFileName(stored.fileName());
        return saveAndScore(profile);
    }

    private CandidateProfileResponse saveAndScore(CandidateProfile profile) {
        profile.setCareerHealth(scoringService.computeCareerHealth(profile));
        return mapper.toResponse(candidateProfileRepository.save(profile));
    }
}
