-- ============================================================================
-- feat/onboarding-my-admin — 수동 실행 SQL (전부 선택 사항)
--
-- Hibernate(ddl-auto=update)가 부팅 시 만드는 것: curation_samples, user_settings, support_inquiries, onboarding_step_logs.
-- 필수 SQL 없음. 아래는 운영 준비·확인용이다.
-- ============================================================================

-- [운영 준비] /meta/policies 와 /me/settings 의 약관·처리방침 URL 은 policies 표의 is_current 행에서 나온다.
--            02_foundation.sql 의 시드(example.com)를 실제 URL·버전으로 바꿔 두어야 한다.
-- UPDATE policies SET url = 'https://…/privacy/v1' WHERE policy_type = 'PRIVACY' AND is_current = TRUE;
-- UPDATE policies SET url = 'https://…/terms/v1'   WHERE policy_type = 'TERMS'   AND is_current = TRUE;

-- [운영 준비] 샘플 큐레이션 — 관리자 API(PUT /admin/curation/samples)로 지정한다. A등급 최소 20건.
--            재산출 전 A 후보를 고르려면:
-- SELECT p.id, p.name, p.view_count FROM products p JOIN product_grades pg ON pg.product_id = p.id AND pg.goal = 'GENERAL' AND pg.eer_band = 0
-- WHERE p.is_active AND pg.grade = 'A' ORDER BY p.view_count DESC LIMIT 40;

-- [설정] 설정 화면의 supportUrl·appVersion 은 application 설정 키 app.support-url · app.app-version (기본 2.0.0-web).
--        재방문 모수 정의 변경 시점은 app.retention.definition-changed-at (기본 2026-09-28).

-- [안전망 · 선택] user_settings 소유자 XOR
-- ALTER TABLE user_settings DROP CONSTRAINT IF EXISTS ck_user_settings_owner_xor,
--     ADD CONSTRAINT ck_user_settings_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

-- [확인] 온보딩 단계 이탈 지점 (최근 7일)
-- SELECT step, action, reason, COUNT(*) FROM onboarding_step_logs WHERE created_at >= now() - interval '7 days'
-- GROUP BY step, action, reason ORDER BY step, action, reason;
