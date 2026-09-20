-- 라이더 풀을 1000명 -> 37만 명으로 늘린다.
-- 이름 자릿수가 4자리(9999)를 넘으므로 기존 1000명도 6자리로 맞춰서 명명 규칙을 통일한다(라이더000001).
-- id는 중간에 결번이 있을 수 있어서 id 순서 기준 번호(ROW_NUMBER)로 이름을 다시 매긴다.
UPDATE riders r
    JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS n FROM riders) x ON r.id = x.id
SET r.name = CONCAT('라이더', LPAD(x.n, 6, '0'));

SET SESSION cte_max_recursion_depth = 400000;

INSERT INTO riders (name, status, created_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1001
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 370000
)
SELECT CONCAT('라이더', LPAD(n, 6, '0')), 'AVAILABLE', NOW()
FROM seq;
