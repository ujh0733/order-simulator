-- 주문 내역 페이지가 필터 없이 "최신순"으로 전체를 정렬/페이징 조회하므로 인덱스를 추가한다.
ALTER TABLE orders
    ADD KEY idx_orders_ordered_at (ordered_at);
