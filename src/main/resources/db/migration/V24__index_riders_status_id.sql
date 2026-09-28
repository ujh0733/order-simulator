-- 라이더 무작위 배차 쿼리(riders.status = 'AVAILABLE')를 위한 인덱스만 먼저 추가한다.
-- 쿼리 자체(ORDER BY RAND())는 아직 바꾸지 않는다 - 인덱스만 추가했을 때 실행계획/성능이
-- 어떻게 달라지는지를 먼저 측정하기 위해서다.
ALTER TABLE riders
    ADD KEY idx_riders_status_id (status, id);
