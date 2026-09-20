-- 가게(merchant)별로 배달지역(region)마다 예상 배달 시간을 관리한다. 60분을 상한으로 둔다.
CREATE TABLE merchant_delivery_estimates (
    id                         BIGINT   NOT NULL AUTO_INCREMENT,
    merchant_id                BIGINT   NOT NULL,
    region_id                  BIGINT   NOT NULL,
    estimated_delivery_minutes INT      NOT NULL,
    created_at                 DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mde_merchant_region (merchant_id, region_id),
    KEY idx_mde_region_id (region_id),
    CONSTRAINT fk_mde_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT fk_mde_region FOREIGN KEY (region_id) REFERENCES regions (id),
    CONSTRAINT chk_mde_minutes CHECK (estimated_delivery_minutes BETWEEN 1 AND 60)
) ENGINE = InnoDB;
