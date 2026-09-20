-- 주문이 접수 -> 조리 -> 배차 -> 완료로 넘어갈 때 필요한 배차 정보와 각 단계 시작 시각을 추가한다.
ALTER TABLE orders
    ADD COLUMN rider_id           BIGINT   NULL AFTER merchant_id,
    ADD COLUMN cooking_started_at DATETIME NULL AFTER estimated_delivery_minutes,
    ADD COLUMN dispatched_at      DATETIME NULL AFTER cooking_started_at,
    ADD COLUMN completed_at       DATETIME NULL AFTER dispatched_at;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_rider FOREIGN KEY (rider_id) REFERENCES riders (id),
    ADD KEY idx_orders_rider_id (rider_id),
    ADD KEY idx_orders_status_cooking_started_at (status, cooking_started_at);
