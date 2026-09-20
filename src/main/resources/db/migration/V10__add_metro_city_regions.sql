-- 서울 외 광역시를 추가한다. "구별로만 1차 세분화" 기준에 맞춰, 자치구(구)로만 구성된
-- 광역시만 우선 추가하고, 군(郡)이 섞여 있는 시/도(경기도 등)는 다음 단계에서 다룬다.
-- external_code는 공공데이터포털 API로 아직 검증하지 않아 NULL로 둔다(서울만 검증됨).

INSERT INTO regions (parent_id, level, name, external_code, created_at)
VALUES (NULL, 'SIDO', '부산광역시', NULL, NOW()),
       (NULL, 'SIDO', '대구광역시', NULL, NOW()),
       (NULL, 'SIDO', '인천광역시', NULL, NOW()),
       (NULL, 'SIDO', '광주광역시', NULL, NOW()),
       (NULL, 'SIDO', '대전광역시', NULL, NOW()),
       (NULL, 'SIDO', '울산광역시', NULL, NOW());

INSERT INTO regions (parent_id, level, name, external_code, created_at)
SELECT r.id, 'GU', gu.name, NULL, NOW()
FROM regions r
         JOIN (
    SELECT '부산광역시' AS sido, '중구' AS name
    UNION ALL SELECT '부산광역시', '서구'
    UNION ALL SELECT '부산광역시', '동구'
    UNION ALL SELECT '부산광역시', '영도구'
    UNION ALL SELECT '부산광역시', '부산진구'
    UNION ALL SELECT '부산광역시', '동래구'
    UNION ALL SELECT '부산광역시', '남구'
    UNION ALL SELECT '부산광역시', '북구'
    UNION ALL SELECT '부산광역시', '해운대구'
    UNION ALL SELECT '부산광역시', '사하구'
    UNION ALL SELECT '부산광역시', '금정구'
    UNION ALL SELECT '부산광역시', '강서구'
    UNION ALL SELECT '부산광역시', '연제구'
    UNION ALL SELECT '부산광역시', '수영구'
    UNION ALL SELECT '부산광역시', '사상구'

    UNION ALL SELECT '대구광역시', '중구'
    UNION ALL SELECT '대구광역시', '동구'
    UNION ALL SELECT '대구광역시', '서구'
    UNION ALL SELECT '대구광역시', '남구'
    UNION ALL SELECT '대구광역시', '북구'
    UNION ALL SELECT '대구광역시', '수성구'
    UNION ALL SELECT '대구광역시', '달서구'

    UNION ALL SELECT '인천광역시', '중구'
    UNION ALL SELECT '인천광역시', '동구'
    UNION ALL SELECT '인천광역시', '미추홀구'
    UNION ALL SELECT '인천광역시', '연수구'
    UNION ALL SELECT '인천광역시', '남동구'
    UNION ALL SELECT '인천광역시', '부평구'
    UNION ALL SELECT '인천광역시', '계양구'
    UNION ALL SELECT '인천광역시', '서구'

    UNION ALL SELECT '광주광역시', '동구'
    UNION ALL SELECT '광주광역시', '서구'
    UNION ALL SELECT '광주광역시', '남구'
    UNION ALL SELECT '광주광역시', '북구'
    UNION ALL SELECT '광주광역시', '광산구'

    UNION ALL SELECT '대전광역시', '동구'
    UNION ALL SELECT '대전광역시', '중구'
    UNION ALL SELECT '대전광역시', '서구'
    UNION ALL SELECT '대전광역시', '유성구'
    UNION ALL SELECT '대전광역시', '대덕구'

    UNION ALL SELECT '울산광역시', '중구'
    UNION ALL SELECT '울산광역시', '남구'
    UNION ALL SELECT '울산광역시', '동구'
    UNION ALL SELECT '울산광역시', '북구'
) AS gu ON gu.sido = r.name
WHERE r.level = 'SIDO';
