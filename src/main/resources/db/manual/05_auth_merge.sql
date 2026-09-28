-- ============================================================================
-- feat/auth-merge — 수동 실행 SQL
--
-- [필수 · 배포 전 또는 직후] users.name · gender · birth_date 의 NOT NULL 해제.
--   register 폐지로 신규 회원은 소셜 로그인에서 이메일만 받는다(프로필 미수집). Hibernate(ddl-auto=update)는 기존 컬럼 제약을 못 바꾼다.
--   이걸 돌리기 전에는 신규 회원의 첫 로그인이 NOT NULL 위반으로 실패한다(기존 회원 로그인·재발급은 정상). 재실행 안전.
-- ============================================================================

ALTER TABLE users
    ALTER COLUMN name       DROP NOT NULL,
    ALTER COLUMN gender     DROP NOT NULL,
    ALTER COLUMN birth_date DROP NOT NULL;

-- [확인] 병합 이력 — 병합된 익명 ID 는 그 뒤로 소유자로 인정되지 않는다
-- SELECT anonymous_id, merged_user_id, merged_at FROM anonymous_users WHERE merged_user_id IS NOT NULL ORDER BY merged_at DESC LIMIT 20;

-- [확인] 신규 가입(프로필 미수집) 회원
-- SELECT id, email, nickname, oauth_provider, created_at FROM users WHERE name IS NULL ORDER BY id DESC LIMIT 20;
