-- Onboarding wizard now branches on "here for a job, or just for fun" (founder's call) and, on
-- the job path, collects structured work-history fields Naukri/LinkedIn-style questionnaires
-- ask (current employer, current/expected CTC, preferred location) - none of this existed on
-- CandidateProfile before. Everything nullable: every one of these fields is skippable in the
-- wizard, and every existing row (all of production's seeded/real profiles) has none of it yet.
ALTER TABLE arena_candidate_profiles ADD COLUMN came_for_job BOOLEAN;
ALTER TABLE arena_candidate_profiles ADD COLUMN organization VARCHAR(255);
ALTER TABLE arena_candidate_profiles ADD COLUMN current_ctc INTEGER;
ALTER TABLE arena_candidate_profiles ADD COLUMN expected_ctc INTEGER;
ALTER TABLE arena_candidate_profiles ADD COLUMN preferred_location VARCHAR(255);
