-- 가게가 동시에 조리할 수 있는 최대 건수. 이 값을 넘으면 신규 주문은 RECEIVED 상태로 대기한다.
ALTER TABLE merchants
    ADD COLUMN max_concurrent_cooking INT NOT NULL DEFAULT 5;
