-- 상태 전이 이벤트 이력. from_status가 NULL이면 최초 접수(RECEIVED) 이벤트.
CREATE TABLE order_status_events (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    order_id    BIGINT      NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status   VARCHAR(20) NOT NULL,
    occurred_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_status_events_order_id (order_id),
    CONSTRAINT fk_order_status_events_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB;
