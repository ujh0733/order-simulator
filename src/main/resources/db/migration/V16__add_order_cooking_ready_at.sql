-- 조리 시작 시점에 "가게별 조리시간"을 더해 미리 계산해두는 컬럼.
-- 이렇게 해두면 배차 대상 조회가 단순 비교(cooking_ready_at <= now)로 끝나서
-- 매번 가게 테이블과 조인해서 시간을 계산할 필요가 없다.
ALTER TABLE orders
    ADD COLUMN cooking_ready_at DATETIME NULL AFTER cooking_started_at;

-- 이제 배차 대상 조회는 cooking_ready_at 기준이라, cooking_started_at 인덱스는 더 이상 안 쓴다.
ALTER TABLE orders
    DROP INDEX idx_orders_status_cooking_started_at,
    ADD KEY idx_orders_status_cooking_ready_at (status, cooking_ready_at);
