-- 주문 생성 시 "영업 중인 가게 무작위 선택"을 ORDER BY RAND()로 하면 53만 건 전체를 정렬해야 해서
-- 매우 느렸다(실측: 초당 1건 미만). id 범위 스캔 방식으로 바꾸면서, 그 스캔이 인덱스를 타도록
-- (business_status_code, id) 복합 인덱스를 추가한다.
ALTER TABLE merchants
    ADD KEY idx_merchants_status_id (business_status_code, id);
