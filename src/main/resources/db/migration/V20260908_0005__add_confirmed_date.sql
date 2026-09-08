-- 확정일(confirmed_date) 기록 컬럼 추가
--
-- LH가 공개한 실제 감사기준 시행세칙 문서를 보면 확정일과 시행일이 다르다
-- (예: 확정 2025.8.29, 시행 2025.9.8). 지금 스키마는 effective_date(시행일)
-- 하나뿐이라 적재 스크립트가 확정일을 잘못 넣으면 아직 시행 전인 규정/매뉴얼이
-- 유효한 것처럼 검색될 위험이 있다.
--
-- confirmed_date는 감사 추적용 기록 컬럼일 뿐, 어떤 유효성 판정 쿼리의 WHERE
-- 절에도 쓰이지 않는다(findValidItemIdsAsOf/findValidManualItemIdsAsOf는 지금처럼
-- effective_date만 본다). nullable + 참조하는 쿼리가 없으므로 기존 조회 결과와
-- 동작에는 영향이 없다.

ALTER TABLE regulations
    ADD COLUMN confirmed_date DATE;

ALTER TABLE audit_manuals
    ADD COLUMN confirmed_date DATE;

COMMENT ON COLUMN regulations.effective_date IS '시행일(확정일 아님) - 이 날짜부터 검색 대상에 포함됨';
COMMENT ON COLUMN regulations.confirmed_date IS '확정일(공포/의결일) - 조회 조건에는 쓰지 않는 기록용 메타데이터';
COMMENT ON COLUMN audit_manuals.effective_date IS '시행일(확정일 아님) - 이 날짜부터 검색 대상에 포함됨';
COMMENT ON COLUMN audit_manuals.confirmed_date IS '확정일(결재 확정일) - 조회 조건에는 쓰지 않는 기록용 메타데이터';
