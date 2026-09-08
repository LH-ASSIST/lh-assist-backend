-- 조문 단위 유효성 판정
--
-- 지금까지는 regulations(문서) 레벨의 effective_date/expiry_date로만 유효성을
-- 판단했다. 그런데 실제 법령 개정은 "제10조만 개정되고 나머지 조항은 그대로"인
-- 경우가 대부분이라, 문서 전체를 구법/신법으로 복제하는 방식은 안 바뀐 조항까지
-- 중복시키는 근사치일 뿐이다.
--
-- reg_items에 자체 effective_date/expiry_date를 추가하되 NULL을 허용해, 조문이
-- 소속 규정과 같은 시점에 발효/만료된다면(대다수) 아무것도 지정하지 않고 규정의
-- 값을 그대로 물려받게 한다. 특정 조문만 별도로 개정된 경우에만 그 조문에 값을
-- 채워 규정 전체를 복제하지 않고도 정밀하게 표현할 수 있다.

ALTER TABLE reg_items
    ADD COLUMN effective_date DATE,
    ADD COLUMN expiry_date DATE;

CREATE INDEX IF NOT EXISTS idx_reg_items_validity
    ON reg_items (effective_date, expiry_date);
