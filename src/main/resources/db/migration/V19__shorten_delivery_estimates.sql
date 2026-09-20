-- 기존에 저장된 가게x지역 예상 배달시간(15~60분)이 너무 길어서, 배달 완료(COMPLETED)까지
-- 가려면 최소 15분을 기다려야 했다. 조리시간과 같은 1~15분 범위로 다시 시드해서
-- 파이프라인 전체(접수~완료)를 짧은 시간 안에 관찰할 수 있게 한다.
UPDATE merchant_delivery_estimates
SET estimated_delivery_minutes = FLOOR(1 + RAND() * 15);
