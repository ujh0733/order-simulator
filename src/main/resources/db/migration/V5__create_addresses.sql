CREATE TABLE addresses (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    region_id       BIGINT       NOT NULL,
    road_address    VARCHAR(200) NOT NULL,
    lot_address     VARCHAR(200) NULL,
    detail_address  VARCHAR(100) NULL,
    zip_code        VARCHAR(10)  NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user_id (user_id),
    KEY idx_addresses_region_id (region_id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_addresses_region FOREIGN KEY (region_id) REFERENCES regions (id)
) ENGINE = InnoDB;
