-- 기존 orders.region_id(단순 배달지역)를 걷어내고, 주문자(user)-배달주소(address)-가게(merchant)를
-- 명시적으로 연결하도록 재구성한다. orders는 아직 더미 데이터뿐이라 컬럼 교체로 처리한다.
ALTER TABLE orders
    DROP FOREIGN KEY fk_orders_region,
    DROP INDEX idx_orders_region_ordered_at,
    DROP COLUMN region_id;

ALTER TABLE orders
    ADD COLUMN user_id     BIGINT NOT NULL AFTER id,
    ADD COLUMN address_id  BIGINT NOT NULL AFTER user_id,
    ADD COLUMN merchant_id BIGINT NOT NULL AFTER address_id,
    ADD COLUMN estimated_delivery_minutes INT NOT NULL AFTER status;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    ADD CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES addresses (id),
    ADD CONSTRAINT fk_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    ADD KEY idx_orders_address_ordered_at (address_id, ordered_at),
    ADD KEY idx_orders_merchant_id (merchant_id);
