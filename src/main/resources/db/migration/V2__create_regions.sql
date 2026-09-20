-- 지역 계층 구조: SIDO(시/도) -> GU(자치구/시/군).
-- 서울 외 지역으로 확장할 때도 이 테이블에 SIDO 로우를 추가하고 그 하위에 GU를 붙이면 된다.
CREATE TABLE regions (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT      NULL,
    level         VARCHAR(10) NOT NULL,
    name          VARCHAR(50) NOT NULL,
    external_code VARCHAR(20) NULL,
    created_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_regions_parent_id (parent_id),
    CONSTRAINT fk_regions_parent FOREIGN KEY (parent_id) REFERENCES regions (id)
) ENGINE = InnoDB;

INSERT INTO regions (parent_id, level, name, external_code, created_at)
VALUES (NULL, 'SIDO', '서울특별시', NULL, NOW());

-- external_code는 공공데이터포털 "행정안전부_식품_일반음식점 조회서비스"의 개방자치단체코드(OPN_ATMY_GRP_CD)와 동일하다.
INSERT INTO regions (parent_id, level, name, external_code, created_at)
SELECT id, 'GU', gu.name, gu.code, NOW()
FROM regions
         CROSS JOIN (
    SELECT '종로구' AS name, '3000000' AS code
    UNION ALL SELECT '중구', '3010000'
    UNION ALL SELECT '용산구', '3020000'
    UNION ALL SELECT '성동구', '3030000'
    UNION ALL SELECT '광진구', '3040000'
    UNION ALL SELECT '동대문구', '3050000'
    UNION ALL SELECT '중랑구', '3060000'
    UNION ALL SELECT '성북구', '3070000'
    UNION ALL SELECT '강북구', '3080000'
    UNION ALL SELECT '도봉구', '3090000'
    UNION ALL SELECT '노원구', '3100000'
    UNION ALL SELECT '은평구', '3110000'
    UNION ALL SELECT '서대문구', '3120000'
    UNION ALL SELECT '마포구', '3130000'
    UNION ALL SELECT '양천구', '3140000'
    UNION ALL SELECT '강서구', '3150000'
    UNION ALL SELECT '구로구', '3160000'
    UNION ALL SELECT '금천구', '3170000'
    UNION ALL SELECT '영등포구', '3180000'
    UNION ALL SELECT '동작구', '3190000'
    UNION ALL SELECT '관악구', '3200000'
    UNION ALL SELECT '서초구', '3210000'
    UNION ALL SELECT '강남구', '3220000'
    UNION ALL SELECT '송파구', '3230000'
    UNION ALL SELECT '강동구', '3240000'
) AS gu
WHERE regions.level = 'SIDO'
  AND regions.name = '서울특별시';
