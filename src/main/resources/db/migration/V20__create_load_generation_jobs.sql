-- 주문 부하 생성기(N건을 M분 동안)의 진행 상태를 DB에 남겨서, 서버가 재시작돼도
-- 남은 건수를 이어서 생성할 수 있게 한다. 이전엔 이 진행 계획이 메모리에만 있어서
-- 재시작하면 통째로 사라졌었다.
CREATE TABLE load_generation_jobs (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    total             INT         NOT NULL,
    completed         INT         NOT NULL DEFAULT 0,
    duration_minutes  INT         NOT NULL,
    started_at        DATETIME    NOT NULL,
    running           BOOLEAN     NOT NULL DEFAULT TRUE,
    failed_message    VARCHAR(500) NULL,
    created_at        DATETIME    NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
