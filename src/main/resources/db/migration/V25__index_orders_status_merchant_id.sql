-- 조리 시작 tick은 가게별 "조리 중 건수"를 한 번에 센다:
--   SELECT merchant_id, COUNT(id) FROM orders WHERE status='COOKING' AND merchant_id IN (...) GROUP BY merchant_id
-- 기존 (status, cooking_ready_at) 인덱스로는 조리 중 주문 전체를 읽고, 행마다 테이블에서 merchant_id를 확인한 뒤
-- 임시 테이블로 묶었다(1회 평균 약 600ms, 약 8,300행).
-- (status, merchant_id)면 인덱스 안에서 가게별 구간만 읽고 정렬된 순서 그대로 묶을 수 있다.
-- merchant_id가 맨 앞인 기존 인덱스는 외래키가 쓰므로 그대로 둔다.
ALTER TABLE orders
    ADD KEY idx_orders_status_merchant_id (status, merchant_id);
