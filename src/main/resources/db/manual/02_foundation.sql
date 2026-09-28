-- ============================================================================
-- refactor/foundation — 수동 실행 SQL
--
-- 이 프로젝트는 spring.jpa.hibernate.ddl-auto=update 라 Hibernate 가 '없는 테이블·컬럼·인덱스·유니크·FK' 는
-- 부팅 시 만들어 준다. 아래는 Hibernate 가 못 하는 것만 모았다:
--   · CHECK 제약 (user_id XOR anonymous_id)   · 부분 유니크 인덱스   · 기존 컬럼 NOT NULL 해제   · 테이블 rename   · 시드 데이터
-- 실행 순서: 이 브랜치 배포 직후, 앱을 한 번 부팅해 새 테이블이 생긴 뒤 아래를 위에서 아래로 실행한다.
-- (rename 이 포함된 섹션은 예외 — 해당 섹션 주석 참조. 배포 전에 실행해야 한다.)
-- 모든 문장은 재실행해도 안전(IF NOT EXISTS / ON CONFLICT).
-- ============================================================================

-- ─── §4 동의·익명 ID·정책 (커밋: 익명 ID·동의·정책 도메인) ────────────────────────────

-- consents: 소유자는 user_id XOR anonymous_id — 정확히 하나만
ALTER TABLE consents
    DROP CONSTRAINT IF EXISTS ck_consents_owner_xor,
    ADD CONSTRAINT ck_consents_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

-- 정책 버전 시드 — 버전·URL 은 팀이 확정한 값으로 바꿀 것. is_current 행이 없으면 서버는 요청 버전을 그대로 기록한다(경고 로그).
INSERT INTO policies (policy_type, version, url, effective_from, is_current)
VALUES ('PRIVACY', 'v1', 'https://example.com/policies/privacy/v1', CURRENT_DATE, TRUE),
       ('TERMS',   'v1', 'https://example.com/policies/terms/v1',   CURRENT_DATE, TRUE),
       ('HEALTH',  'v1', 'https://example.com/policies/health/v1',  CURRENT_DATE, TRUE)
ON CONFLICT (policy_type, version) DO NOTHING;

-- ─── §5 목표 (커밋: user_goals + /me/goal) ────────────────────────────────────────────

ALTER TABLE user_goals
    DROP CONSTRAINT IF EXISTS ck_user_goals_owner_xor,
    ADD CONSTRAINT ck_user_goals_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

-- ─── §6 저장 (커밋: user_favorites 개조 → /me/saved-products) ─────────────────────────
-- 물리 테이블명은 user_favorites 유지(엔티티 SavedProduct). anonymous_id 컬럼은 Hibernate 가 부팅 시 추가한다.
-- 아래를 돌리기 전까지 익명 저장은 user_id NOT NULL 위반으로 실패한다(로그인 저장은 정상).

ALTER TABLE user_favorites ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE user_favorites
    DROP CONSTRAINT IF EXISTS ck_user_favorites_owner_xor,
    ADD CONSTRAINT ck_user_favorites_owner_xor CHECK ((user_id IS NULL) <> (anonymous_id IS NULL));

-- 익명 소유의 중복 저장 방지 (부분 유니크 — Hibernate 가 못 만든다). (user_id, product_id) 유니크는 기존 그대로.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_favorites_anon_product
    ON user_favorites (anonymous_id, product_id) WHERE anonymous_id IS NOT NULL;

-- (선택) 테이블명을 ERD 대로 saved_products 로 바꾸려면 아래를 실행하고 SavedProduct 의 @Table(name) 도 같이 바꿀 것.
-- 반드시 그 코드 배포 '전'에 실행해야 한다(ddl-auto 가 빈 saved_products 를 새로 만들어 버림).
-- ALTER TABLE user_favorites RENAME TO saved_products;
