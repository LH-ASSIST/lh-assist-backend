-- 확정일이 시행일보다 나중일 수 없다는 제약을 스키마 수준에서 강제
--
-- confirmed_date는 항상 NULL이거나 effective_date 이전이어야 한다("확정되기도
-- 전에 시행될 수는 없다"). 지금까지 적재된 모든 행은 confirmed_date가 NULL이라
-- 이 제약이 어떤 기존 행도 위반하지 않는다 - 순수하게 앞으로의 적재 실수를
-- 막기 위한 안전장치다.

ALTER TABLE regulations
    ADD CONSTRAINT chk_regulations_confirmed_before_effective
    CHECK (confirmed_date IS NULL OR confirmed_date <= effective_date);

ALTER TABLE audit_manuals
    ADD CONSTRAINT chk_audit_manuals_confirmed_before_effective
    CHECK (confirmed_date IS NULL OR confirmed_date <= effective_date);
