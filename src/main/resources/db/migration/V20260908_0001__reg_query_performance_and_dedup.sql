-- 기준일 기반 규정 유효성 조회 성능 및 데이터 무결성 개선
--
-- 1) 조문 단위 유효성 조회(findValidItemIdsAsOf)가 regulations를 조인해 effective_date/
--    expiry_date/is_active로 필터링하므로, 복합 인덱스가 없으면 규정 수가 늘어날 때
--    풀 스캔이 된다.
-- 2) reg_items -> regulations FK는 있지만 Postgres는 FK 컬럼에 자동으로 인덱스를 만들지
--    않으므로 조인 성능을 위해 별도로 추가한다.
-- 3) 적재 스크립트(vector.py) 재실행 시 같은 규정 안에서 같은 조문이 중복 삽입되는 것을
--    막기 위해 (reg_id, content_hash)에 유니크 제약을 건다. content_hash 단독으로 걸면
--    구법과 신법이 동일 조문을 그대로 승계하는 정상적인 경우(서로 다른 reg_id)까지
--    막아버리므로 reg_id를 포함한 복합 유니크로 범위를 좁힌다.
--    기존에 이미 들어간 중복 행은 먼저 정리한다
--    (parent_id로 참조되는 행이 없음을 확인했으므로 안전하게 삭제 가능).

CREATE INDEX IF NOT EXISTS idx_regulations_validity
    ON regulations (effective_date, expiry_date, is_active);

CREATE INDEX IF NOT EXISTS idx_reg_items_reg_id
    ON reg_items (reg_id);

DELETE FROM reg_items a
    USING reg_items b
    WHERE a.item_id > b.item_id
      AND a.reg_id = b.reg_id
      AND a.content_hash = b.content_hash;

ALTER TABLE reg_items
    ADD CONSTRAINT uq_reg_items_reg_id_content_hash UNIQUE (reg_id, content_hash);
