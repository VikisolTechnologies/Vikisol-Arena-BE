\copy arena_users (id, created_at, updated_at, email, name, password_hash, role, date_of_birth) from 'users.csv' csv
\copy arena_candidate_profiles (id, created_at, updated_at, autonomy, avatar_emoji, career_health, auto_apply, searchable_by_enterprises, experience_years, industry, location, name, rate_floor, remote, title, user_id, location_consent, geohash, approx_lat, approx_lng, home_city) from 'profiles.csv' csv
\copy arena_enterprise_profiles (id, created_at, updated_at, company_name, industry, logo_emoji, plan, seats_total, seats_used, size, unlock_credits_total, unlock_credits_used, user_id) from 'companies.csv' csv
\copy arena_posts (id, created_at, updated_at, author_user_id, intent_type, title, body, location_text, audience, visibility, capacity, spots_filled, status, starts_at, geohash, approx_lat, approx_lng, anonymous) from 'posts.csv' csv
\copy arena_post_tags (post_id, tag) from 'tags.csv' csv
\copy arena_post_joins (id, created_at, updated_at, post_id, user_id, status) from 'joins.csv' csv
\copy arena_follows (id, created_at, updated_at, follower_user_id, following_user_id, target_type) from 'follows.csv' csv
\copy arena_job_postings (id, created_at, updated_at, description, employment_type, industry, location, remote, salary_max, salary_min, status, title, enterprise_id, work_mode) from 'jobs.csv' csv
\copy arena_job_posting_skills (posting_id, skill) from 'skills.csv' csv
\copy arena_conversations (id, created_at, updated_at, last_message_at, user_a_id, user_b_id, context) from 'convs.csv' csv
\copy arena_thread_messages (id, created_at, updated_at, content, conversation_id, sender_user_id) from 'msgs.csv' csv
update arena_posts p set spots_filled = (select count(*) from arena_post_joins j where j.post_id = p.id and j.status = 'APPROVED');
analyze;
