-- 감사 근거의 출처 무결성 강화
--
-- analysis_evidences.source_id는 지금까지 느슨한 문자열이라 reg_items/audit_manual_items/
-- audit_items 어느 것도 실제로 참조하는지 DB가 보장해주지 않았다. AI가 존재하지 않는
-- 조항 id를 근거로 들어도 그대로 저장되고, 몇 년 뒤 감사 판정의 근거를 재현하려 해도
-- "그 시점에 어떤 버전의 규정이었는지"를 신뢰성 있게 되짚을 수 없었다.
--
-- source_type별로 정확히 하나의 FK만 채워지도록 강제하고, REG_ITEM 근거에는 판정 시점의
-- 규정 버전/발효일을 스냅샷으로 남겨 규정이 이후에 개정되어도 당시 근거를 그대로 재현할
-- 수 있게 한다.

ALTER TABLE analysis_evidences
    ADD COLUMN reg_item_id BIGINT REFERENCES reg_items (item_id),
    ADD COLUMN audit_manual_item_id BIGINT REFERENCES audit_manual_items (manual_item_id),
    ADD COLUMN audit_item_id BIGINT REFERENCES audit_items (item_id),
    ADD COLUMN reg_version_snapshot VARCHAR(100),
    ADD COLUMN reg_effective_date_snapshot DATE;

-- 기존 문자열 source_id를 최선을 다해 타입별 FK 컬럼으로 이관한다.
UPDATE analysis_evidences
SET reg_item_id = source_id::bigint
WHERE source_type = 'REG_ITEM'
  AND source_id ~ '^[0-9]+$';

UPDATE analysis_evidences
SET audit_manual_item_id = source_id::bigint
WHERE source_type = 'AUDIT_MANUAL_ITEM'
  AND source_id ~ '^[0-9]+$';

UPDATE analysis_evidences
SET audit_item_id = source_id::bigint
WHERE source_type = 'AUDIT_ITEM'
  AND source_id ~ '^[0-9]+$';

UPDATE analysis_evidences ae
SET reg_version_snapshot = r.version,
    reg_effective_date_snapshot = r.effective_date
FROM reg_items ri
    JOIN regulations r ON r.reg_id = ri.reg_id
WHERE ae.reg_item_id = ri.item_id;

ALTER TABLE analysis_evidences
    ADD CONSTRAINT chk_analysis_evidences_source_ref CHECK (
        (source_type = 'REG_ITEM'
            AND reg_item_id IS NOT NULL
            AND audit_manual_item_id IS NULL
            AND audit_item_id IS NULL)
        OR (source_type = 'AUDIT_MANUAL_ITEM'
            AND audit_manual_item_id IS NOT NULL
            AND reg_item_id IS NULL
            AND audit_item_id IS NULL)
        OR (source_type = 'AUDIT_ITEM'
            AND audit_item_id IS NOT NULL
            AND reg_item_id IS NULL
            AND audit_manual_item_id IS NULL)
    );

CREATE INDEX IF NOT EXISTS idx_analysis_evidences_reg_item_id
    ON analysis_evidences (reg_item_id);
