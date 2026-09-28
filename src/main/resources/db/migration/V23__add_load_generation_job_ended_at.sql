-- 목표 시간(duration_minutes)을 넘겨서 끝나는 경우를 실측하기 위해, 작업이 실제로 끝난 시각을 남긴다.
-- 생성 속도가 목표 배분 속도를 못 따라가면(=병목) started_at + duration_minutes보다 늦게 끝날 수 있다.
ALTER TABLE load_generation_jobs
    ADD COLUMN ended_at DATETIME NULL;
