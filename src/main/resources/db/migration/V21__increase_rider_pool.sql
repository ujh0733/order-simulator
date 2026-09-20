-- 라이더 풀을 50명 -> 1000명으로 늘린다. 동시 배송 가능 건수의 자연스러운 상한이 그만큼 커진다.
-- 기존 50명 이름(라이더001)도 4자리로 맞춰서 전체 명명 규칙을 통일한다(라이더0001).
UPDATE riders
SET name = CONCAT('라이더', LPAD(id, 4, '0'));

SET SESSION cte_max_recursion_depth = 2000;

INSERT INTO riders (name, status, created_at)
WITH RECURSIVE seq(n) AS (
    SELECT 51
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 1000
)
SELECT CONCAT('라이더', LPAD(n, 4, '0')), 'AVAILABLE', NOW()
FROM seq;
