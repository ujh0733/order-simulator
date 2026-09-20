CREATE TABLE riders (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- 더미 라이더 풀 50명 시드.
INSERT INTO riders (name, status, created_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 50
)
SELECT CONCAT('라이더', LPAD(n, 3, '0')), 'AVAILABLE', NOW()
FROM seq;
