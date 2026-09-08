-- 데모/검증용 시드 스크립트 (Flyway 마이그레이션 아님, 수동 실행 전용)
--
-- rag_data.dump를 복원하면 모든 regulations.effective_date가 "적재한 날짜"로만
-- 채워져 있어(V7 마이그레이션 코멘트 참고), 기준일 기반 유효 규정 필터링을
-- 과거 시점 기준일로 실제 시연/테스트할 데이터가 없다.
--
-- 이 스크립트는 "공공주택 특별법"(reg_id=10)을 기준으로, 신법 발효일과 정확히
-- 맞닿는 구법 버전을 하나 만들어 아래 시나리오를 실제 DB로 재현 가능하게 한다.
--   - 과거 기준일(예: 2020-01-01)로 조회 → 구법만 유효 규정으로 반환
--   - 신법 발효일 당일(2026-02-04)로 조회 → 신법만 반환, 구법은 그날부터 제외
--   - 과도기 조회(발효~만료 사이) → 구법/신법 동시 유효 구간이 필요하면 expiry_date를
--     발효일 이후로 늦춰서 별도로 테스트할 것
--
-- 구법 조문은 실제 개정 이력 텍스트가 없어 신법 조문 중 일부를 그대로 복사해
-- 재사용한다(임베딩 재호출 비용 없이 파이프라인 동작만 검증하기 위함).
-- 실제 법령 문언의 역사적 정확성을 담보하지 않으므로 데모/로컬 검증 용도로만 사용할 것.

BEGIN;

INSERT INTO regulations (
    title, reg_type, effective_date, expiry_date, is_active,
    amendment_date, version, source_url, created_at, updated_at
)
SELECT
    title || ' (개정 전, 데모용)',
    reg_type,
    DATE '2015-01-01',
    effective_date,               -- 신법 발효일과 정확히 맞닿는 만료일 (경계값 테스트용)
    true,
    DATE '2015-01-01',
    'legacy-demo',
    source_url,
    NOW(),
    NOW()
FROM regulations
WHERE reg_id = 10
RETURNING reg_id AS old_reg_id \gset

INSERT INTO reg_items (
    clause_number, content, chunk_index, page_number, section_title,
    content_hash, change_type, is_mandatory, embedding_model, vector_index, reg_id,
    created_at, updated_at
)
SELECT
    clause_number, content, chunk_index, page_number, section_title,
    content_hash, 'AMENDED', is_mandatory, embedding_model, vector_index, :old_reg_id,
    NOW(), NOW()
FROM reg_items
WHERE reg_id = 10
ORDER BY item_id
LIMIT 5;

COMMIT;
