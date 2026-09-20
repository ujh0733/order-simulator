package dev.junghun.ordersimulator.datacollector;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터포털의 "행정안전부_식품_일반음식점 조회서비스"
 * (https://www.data.go.kr/data/15154916/openapi.do, Base URL: apis.data.go.kr/1741000/general_restaurants)
 * 에서 서울시 25개 자치구의 일반음식점 인허가 정보를 수집한다.
 * <p>
 * 서울 열린데이터광장의 자치구별 데이터셋(예: OA-16094 등)은 서비스가 종료되었고,
 * 현재는 위 통합 API를 cond[OPN_ATMY_GRP_CD::EQ]=개방자치단체코드 로 자치구별 필터링해 사용해야 한다.
 */
public final class SeoulRestaurantDataCollector {

    private static final String BASE_URL = "https://apis.data.go.kr/1741000/general_restaurants/info";
    private static final int MAX_ROWS_PER_PAGE = 100;
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("data", "seoul-restaurants");

    // 개방자치단체코드(OPN_ATMY_GRP_CD): 행정표준코드(법정동코드)와는 다른 이 API 전용 코드.
    // 실제 API를 cond[ROAD_NM_ADDR::LIKE]="서울특별시 OO구" 로 조회해 각 구의 코드를 직접 확인해 채움.
    private static final Map<String, String> SEOUL_DISTRICT_CODES = Map.ofEntries(
            Map.entry("종로구", "3000000"),
            Map.entry("중구", "3010000"),
            Map.entry("용산구", "3020000"),
            Map.entry("성동구", "3030000"),
            Map.entry("광진구", "3040000"),
            Map.entry("동대문구", "3050000"),
            Map.entry("중랑구", "3060000"),
            Map.entry("성북구", "3070000"),
            Map.entry("강북구", "3080000"),
            Map.entry("도봉구", "3090000"),
            Map.entry("노원구", "3100000"),
            Map.entry("은평구", "3110000"),
            Map.entry("서대문구", "3120000"),
            Map.entry("마포구", "3130000"),
            Map.entry("양천구", "3140000"),
            Map.entry("강서구", "3150000"),
            Map.entry("구로구", "3160000"),
            Map.entry("금천구", "3170000"),
            Map.entry("영등포구", "3180000"),
            Map.entry("동작구", "3190000"),
            Map.entry("관악구", "3200000"),
            Map.entry("서초구", "3210000"),
            Map.entry("강남구", "3220000"),
            Map.entry("송파구", "3230000"),
            Map.entry("강동구", "3240000")
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serviceKey;

    public SeoulRestaurantDataCollector(String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalArgumentException("공공데이터포털에서 발급받은 서비스키(serviceKey)가 필요합니다.");
        }
        this.serviceKey = serviceKey;
    }

    /** 자치구 하나의 전체 페이지를 조회해 합친다. */
    public List<RestaurantItem> fetchDistrict(String districtName, String districtCode) {
        List<RestaurantItem> result = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE;

        while ((long) (pageNo - 1) * MAX_ROWS_PER_PAGE < totalCount) {
            JsonNode body = requestPage(districtCode, pageNo);
            totalCount = body.path("totalCount").asInt(0);

            JsonNode itemNode = body.path("items").path("item");
            if (itemNode.isArray()) {
                for (JsonNode item : itemNode) {
                    result.add(toRestaurantItem(item, districtName, districtCode));
                }
            } else if (itemNode.isObject()) {
                result.add(toRestaurantItem(itemNode, districtName, districtCode));
            }

            pageNo++;
            sleepQuietly(150);
        }

        return result;
    }

    /** 서울시 25개 자치구 전체를 순회하며 최신 데이터로 갱신한다. */
    public Map<String, List<RestaurantItem>> fetchAllSeoulDistricts() {
        return fetchAllSeoulDistricts((districtName, items) -> { });
    }

    /**
     * 서울시 25개 자치구 전체를 순회하며 최신 데이터로 갱신한다.
     * 구 하나가 끝날 때마다 onDistrictDone 콜백으로 즉시 알려줘서, 전체가 끝나기 전에도 진행 상황을 확인할 수 있다.
     */
    public Map<String, List<RestaurantItem>> fetchAllSeoulDistricts(
            java.util.function.BiConsumer<String, List<RestaurantItem>> onDistrictDone) {
        Map<String, List<RestaurantItem>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> district : SEOUL_DISTRICT_CODES.entrySet()) {
            List<RestaurantItem> items = fetchDistrict(district.getKey(), district.getValue());
            result.put(district.getKey(), items);
            System.out.printf("[%s] %d건 수집 완료%n", district.getKey(), items.size());
            onDistrictDone.accept(district.getKey(), items);
        }
        return result;
    }

    private static final int MAX_RETRIES = 5;

    // 공공데이터포털 API가 대량 연속 요청 시 503(SERVICETIMEOUT_ERROR)을 간헐적으로 반환해 재시도가 필요하다.
    private JsonNode requestPage(String districtCode, int pageNo) {
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return requestPageOnce(districtCode, pageNo);
            } catch (IllegalStateException e) {
                lastError = e;
                System.err.printf(
                        "요청 실패(%d/%d) 구코드=%s 페이지=%d: %s%n",
                        attempt, MAX_RETRIES, districtCode, pageNo, e.getMessage());
                sleepQuietly(1000L * attempt);
            }
        }
        throw lastError;
    }

    private JsonNode requestPageOnce(String districtCode, int pageNo) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "?" + buildQuery(districtCode, pageNo)))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("API 응답 오류 (HTTP " + response.statusCode() + "): " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asString("");
            if (!"0".equals(resultCode) && !"00".equals(resultCode)) {
                throw new IllegalStateException("API 오류 [" + resultCode + "] " + header.path("resultMsg").asString());
            }

            return root.path("response").path("body");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "서울시 일반음식점 데이터 요청 실패: 구코드=" + districtCode + ", 페이지=" + pageNo, e);
        }
    }

    private String buildQuery(String districtCode, int pageNo) {
        StringBuilder sb = new StringBuilder();
        // serviceKey는 공공데이터포털에서 받은 "Decoding" 키를 넣어야 한다.
        // 이미 URL 인코딩된 "Encoding" 키를 넣으면 이중 인코딩되어 인증에 실패한다.
        appendParam(sb, "serviceKey", serviceKey);
        appendParam(sb, "pageNo", String.valueOf(pageNo));
        appendParam(sb, "numOfRows", String.valueOf(MAX_ROWS_PER_PAGE));
        appendParam(sb, "returnType", "JSON");
        appendParam(sb, "cond[OPN_ATMY_GRP_CD::EQ]", districtCode);
        return sb.toString();
    }

    private void appendParam(StringBuilder sb, String name, String value) {
        if (!sb.isEmpty()) {
            sb.append('&');
        }
        sb.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private RestaurantItem toRestaurantItem(JsonNode item, String districtName, String districtCode) {
        return new RestaurantItem(
                districtName,
                districtCode,
                item.path("MNG_NO").asString(null),
                item.path("BPLC_NM").asString(null),
                item.path("SALS_STTS_CD").asString(null),
                item.path("SALS_STTS_NM").asString(null),
                item.path("DTL_SALS_STTS_NM").asString(null),
                item.path("LCPMT_YMD").asString(null),
                item.path("CLSBIZ_YMD").asString(null),
                item.path("ROAD_NM_ADDR").asString(null),
                item.path("LOTNO_ADDR").asString(null),
                item.path("TELNO").asString(null),
                item.path("CRD_INFO_X").asString(null),
                item.path("CRD_INFO_Y").asString(null)
        );
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record RestaurantItem(
            String districtName,
            String districtCode,
            String managementNo,
            String businessName,
            String businessStatusCode,
            String businessStatusName,
            String detailStatusName,
            String licenseDate,
            String closeDate,
            String roadAddress,
            String lotAddress,
            String phone,
            String coordX,
            String coordY
    ) {
    }

    /** 수동 실행 진입점. {@code ./gradlew collectSeoulRestaurants} 로 언제든 최신화할 수 있다. */
    public static void main(String[] args) throws Exception {
        String serviceKey = args.length > 0 ? args[0] : System.getenv("SEOUL_API_SERVICE_KEY");
        if (serviceKey == null || serviceKey.isBlank()) {
            System.err.println("SEOUL_API_SERVICE_KEY 환경변수를 설정하거나 첫 번째 인자로 서비스키를 전달하세요.");
            System.exit(1);
            return;
        }

        Files.createDirectories(DEFAULT_OUTPUT_DIR);
        ObjectMapper writer = new ObjectMapper();

        try (MerchantDbWriter dbWriter = new MerchantDbWriter()) {
            SeoulRestaurantDataCollector collector = new SeoulRestaurantDataCollector(serviceKey);
            Map<String, List<RestaurantItem>> byDistrict = collector.fetchAllSeoulDistricts((districtName, items) -> {
                Path outputPath = DEFAULT_OUTPUT_DIR.resolve(districtName + ".json");
                writer.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), items);

                try {
                    int saved = dbWriter.save(districtName, items);
                    System.out.printf("[%s] DB 저장 완료 (%d건 upsert)%n", districtName, saved);
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException("DB 저장 실패: " + districtName, e);
                }
            });

            int total = byDistrict.values().stream().mapToInt(List::size).sum();
            System.out.printf(
                    "전체 %d개 자치구, 총 %d건 저장 완료 (%s, DB merchants 테이블 포함)%n",
                    byDistrict.size(), total, DEFAULT_OUTPUT_DIR.toAbsolutePath());
        }
    }
}
