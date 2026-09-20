package dev.junghun.ordersimulator.datacollector;

import dev.junghun.ordersimulator.datacollector.SeoulRestaurantDataCollector.RestaurantItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 수집한 가게 데이터를 merchants 테이블에 upsert한다(management_no 기준이라 다시 실행해도 안전하다). */
final class MerchantDbWriter implements AutoCloseable {

    private static final String JDBC_URL =
            "jdbc:mysql://localhost:3306/order_simulator?characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
                    + "&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true";
    private static final String DB_USER = "app";
    private static final String DB_PASSWORD = "app";
    // API 문서에는 YYYYMMDD로 안내되어 있지만, 실제 응답은 "2026-09-15"처럼 하이픈이 포함된 형식이다.
    private static final DateTimeFormatter[] API_DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyyMMdd"),
    };

    private static final String UPSERT_SQL = """
            INSERT INTO merchants (
                region_id, management_no, name, business_status_code, business_status_name,
                detail_status_name, license_date, close_date, road_address, lot_address, phone,
                coord_x, coord_y, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                region_id = VALUES(region_id),
                name = VALUES(name),
                business_status_code = VALUES(business_status_code),
                business_status_name = VALUES(business_status_name),
                detail_status_name = VALUES(detail_status_name),
                license_date = VALUES(license_date),
                close_date = VALUES(close_date),
                road_address = VALUES(road_address),
                lot_address = VALUES(lot_address),
                phone = VALUES(phone),
                coord_x = VALUES(coord_x),
                coord_y = VALUES(coord_y)
            """;

    private final Connection connection;
    private final Map<String, Long> regionIdByName = new HashMap<>();

    MerchantDbWriter() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC 드라이버를 찾을 수 없습니다.", e);
        }
        this.connection = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
        this.connection.setAutoCommit(false);
    }

    /** districtName(GU) 하나의 수집 결과를 merchants 테이블에 저장하고, 저장된 건수를 반환한다. */
    int save(String districtName, List<RestaurantItem> items) throws SQLException {
        long regionId = regionIdFor(districtName);
        int saved = 0;

        try (PreparedStatement stmt = connection.prepareStatement(UPSERT_SQL)) {
            for (RestaurantItem item : items) {
                if (item.managementNo() == null || item.managementNo().isBlank()) {
                    continue;
                }
                stmt.setLong(1, regionId);
                stmt.setString(2, item.managementNo());
                stmt.setString(3, item.businessName());
                stmt.setString(4, item.businessStatusCode());
                stmt.setString(5, item.businessStatusName());
                stmt.setString(6, item.detailStatusName());
                setNullableDate(stmt, 7, item.licenseDate());
                setNullableDate(stmt, 8, item.closeDate());
                stmt.setString(9, item.roadAddress());
                stmt.setString(10, item.lotAddress());
                stmt.setString(11, item.phone());
                setNullableDecimal(stmt, 12, item.coordX());
                setNullableDecimal(stmt, 13, item.coordY());
                stmt.setTimestamp(14, Timestamp.valueOf(LocalDateTime.now()));
                stmt.addBatch();
                saved++;
            }
            stmt.executeBatch();
            connection.commit();
            return saved;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    private long regionIdFor(String districtName) throws SQLException {
        Long cached = regionIdByName.get(districtName);
        if (cached != null) {
            return cached;
        }
        try (PreparedStatement stmt =
                     connection.prepareStatement("SELECT id FROM regions WHERE level = 'GU' AND name = ?")) {
            stmt.setString(1, districtName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("regions 테이블에 없는 자치구입니다: " + districtName);
                }
                long id = rs.getLong("id");
                regionIdByName.put(districtName, id);
                return id;
            }
        }
    }

    private void setNullableDate(PreparedStatement stmt, int index, String rawDate) throws SQLException {
        if (rawDate == null || rawDate.isBlank()) {
            stmt.setNull(index, Types.DATE);
            return;
        }
        for (DateTimeFormatter format : API_DATE_FORMATS) {
            try {
                stmt.setDate(index, Date.valueOf(LocalDate.parse(rawDate, format)));
                return;
            } catch (Exception ignored) {
                // 다음 포맷으로 재시도
            }
        }
        stmt.setNull(index, Types.DATE);
    }

    private void setNullableDecimal(PreparedStatement stmt, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            stmt.setNull(index, Types.DECIMAL);
            return;
        }
        try {
            stmt.setBigDecimal(index, new BigDecimal(value));
        } catch (NumberFormatException e) {
            stmt.setNull(index, Types.DECIMAL);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
