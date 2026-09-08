-- 감사매뉴얼 시점 유효성 판정
--
-- reg_items/regulations는 기준일 기반 유효성 필터링이 적용되지만, audit_manuals(LH
-- 자체 감사매뉴얼)에는 effective_date/expiry_date/is_active 컬럼 자체가 없어 필터링
-- 대상이 아니었다. 감사매뉴얼은 법령보다 개정 관리가 느슨한 경우가 많아, 폐기된
-- 매뉴얼 조항이 최신 판정 근거로 그대로 검색될 수 있는 사각지대였다.
--
-- 감사매뉴얼은 보통 한 번 개정되면 문서 전체가 통째로 새 버전으로 교체되므로
-- (조문 단위로 부분 개정되는 법령과 달리), reg_items처럼 조문 단위가 아니라
-- audit_manuals 단위로 유효성을 관리한다.

ALTER TABLE audit_manuals
    ADD COLUMN effective_date DATE,
    ADD COLUMN expiry_date DATE,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- 기존에 적재된 매뉴얼은 발효일 정보가 없으므로, 소급 배제되지 않도록 시스템 도입
-- 이전부터 유효했던 것으로 간주한다(운영에서 실제 시행일로 값을 채워 넣어야 한다).
UPDATE audit_manuals
SET effective_date = '1900-01-01'
WHERE effective_date IS NULL;

ALTER TABLE audit_manuals
    ALTER COLUMN effective_date SET NOT NULL;

-- audit_manuals에는 유효성 컬럼 복합 인덱스를 별도로 만들지 않는다. rag_data.dump
-- 실제 데이터를 복원해 확인한 결과 audit_manuals는 1행, audit_manual_items는
-- 166행뿐이다(reg_items 1,807행/regulations 11행과 비교해도 압도적으로 작다).
-- 사용자 생성 데이터가 아니라 LH가 관리하는 감사매뉴얼 문서 자체가 소수라 이
-- 규모 자체가 원천적으로 작다. 300행/2만행 가정으로 EXPLAIN ANALYZE를 돌려봐도
-- 옵티마이저가 인덱스를 쓰지 않고 Seq Scan을 선택했으니(비용 8.50, 0.127ms,
-- 버퍼 4개), 실제 1행에서는 말할 것도 없다. 인덱스는 조회 이득 없이 매뉴얼
-- 등록/개정 시 쓰기 비용만 추가하므로 만들지 않는다.
