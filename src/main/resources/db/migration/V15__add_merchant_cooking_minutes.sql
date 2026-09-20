-- 가게별 조리 소요시간(분). 고정 10분 대신 가게마다 다르게 둬서,
-- 조리가 빠른 가게는 배차/완료까지 빠르게 넘어가는 걸 눈으로 볼 수 있게 한다.
ALTER TABLE merchants
    ADD COLUMN cooking_minutes INT NOT NULL DEFAULT 10;

-- 1~15분 사이 무작위 분포. 약 1/15은 1분짜리(초고속) 가게가 된다.
UPDATE merchants
SET cooking_minutes = FLOOR(1 + RAND() * 15);
