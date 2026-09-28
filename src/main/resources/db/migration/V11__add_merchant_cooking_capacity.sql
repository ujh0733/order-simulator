-- 가게가 동시에 조리할 수 있는 최대 건수. 이 값을 넘으면 신규 주문은 RECEIVED 상태로 대기한다.
ALTER TABLE merchants
    ADD COLUMN max_concurrent_cooking INT NOT NULL DEFAULT 5;

-- 전 가게가 똑같이 5면 가게 간 차이가 없어서, 이 시점에 있는 가게는 3~10 사이 무작위로 정한다.
UPDATE merchants
SET max_concurrent_cooking = FLOOR(3 + RAND() * 8);
