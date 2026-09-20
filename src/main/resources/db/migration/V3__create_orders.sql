CREATE TABLE orders (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    region_id  BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    ordered_at DATETIME    NOT NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_region_ordered_at (region_id, ordered_at),
    CONSTRAINT fk_orders_region FOREIGN KEY (region_id) REFERENCES regions (id)
) ENGINE = InnoDB;
