package dev.junghun.ordersimulator.seed

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 주문 내역 페이지/통계용 이력 데이터를 대량 생성한다. 실시간 파이프라인(부하 생성기)으로
 * 100만 건을 만들면 하루 이상 걸리므로(라이더 풀·조리 용량 제한을 실제로 흉내내는 구조라 느림),
 * 이건 그 제약을 거치지 않고 이미 끝난 과거 주문(COMPLETED)을 DummyUserSeeder와 같은 방식의
 * JDBC 배치insert로 바로 채워 넣는다.
 */
private const val JDBC_URL =
    "jdbc:mysql://localhost:3306/order_simulator?characterEncoding=UTF-8&serverTimezone=Asia/Seoul" +
        "&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true"
private const val DB_USER = "app"
private const val DB_PASSWORD = "app"

private const val ORDER_COUNT = 1_000_000
private const val BATCH_SIZE = 5_000
private const val SAMPLE_SIZE = 20_000
private const val HISTORY_DAYS = 90L
private const val MIN_DELIVERY_MINUTES = 1
private const val MAX_DELIVERY_MINUTES = 15

private data class AddressSample(val id: Long, val userId: Long)

fun main() {
    Class.forName("com.mysql.cj.jdbc.Driver")
    DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD).use { connection ->
        connection.autoCommit = false

        println("샘플링 중...")
        val addresses = sampleAddresses(connection, SAMPLE_SIZE)
        val merchantIds = sampleActiveMerchants(connection, SAMPLE_SIZE)
        val riderIds = fetchAllRiderIds(connection)
        check(addresses.isNotEmpty()) { "주소 데이터가 없습니다. seedDummyUsers를 먼저 실행하세요." }
        check(merchantIds.isNotEmpty()) { "영업 중인 가게가 없습니다. collectSeoulRestaurants를 먼저 실행하세요." }
        check(riderIds.isNotEmpty()) { "라이더 데이터가 없습니다." }
        println("주소 ${addresses.size}건, 가게 ${merchantIds.size}건, 라이더 ${riderIds.size}건 샘플링 완료. 주문 ${ORDER_COUNT}건 생성 시작.")

        val sql = """
            INSERT INTO orders (
                user_id, address_id, merchant_id, rider_id, status, estimated_delivery_minutes,
                cooking_started_at, cooking_ready_at, dispatched_at, completed_at, ordered_at, created_at
            ) VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val now = LocalDateTime.now()
        var inserted = 0

        connection.prepareStatement(sql).use { stmt ->
            var batchCount = 0
            repeat(ORDER_COUNT) {
                val address = addresses.random()
                val merchantId = merchantIds.random()
                val riderId = riderIds.random()
                val deliveryMinutes = Random.nextInt(MIN_DELIVERY_MINUTES, MAX_DELIVERY_MINUTES + 1)

                val orderedAt = now.minusSeconds(Random.nextLong(0, HISTORY_DAYS * 24 * 3600))
                val cookingStartedAt = orderedAt.plusSeconds(Random.nextLong(5, 60))
                val cookingReadyAt = cookingStartedAt.plusMinutes(
                    Random.nextLong(MIN_DELIVERY_MINUTES.toLong(), MAX_DELIVERY_MINUTES + 1L)
                )
                val dispatchedAt = cookingReadyAt.plusSeconds(Random.nextLong(0, 300))
                val completedAt = dispatchedAt.plusMinutes(deliveryMinutes.toLong())

                stmt.setLong(1, address.userId)
                stmt.setLong(2, address.id)
                stmt.setLong(3, merchantId)
                stmt.setLong(4, riderId)
                stmt.setInt(5, deliveryMinutes)
                stmt.setTimestamp(6, Timestamp.valueOf(cookingStartedAt))
                stmt.setTimestamp(7, Timestamp.valueOf(cookingReadyAt))
                stmt.setTimestamp(8, Timestamp.valueOf(dispatchedAt))
                stmt.setTimestamp(9, Timestamp.valueOf(completedAt))
                stmt.setTimestamp(10, Timestamp.valueOf(orderedAt))
                stmt.setTimestamp(11, Timestamp.valueOf(orderedAt))
                stmt.addBatch()
                batchCount++

                if (batchCount >= BATCH_SIZE) {
                    stmt.executeBatch()
                    connection.commit()
                    inserted += batchCount
                    batchCount = 0
                    if (inserted % 100_000 == 0) println("진행: ${inserted}건")
                }
            }
            if (batchCount > 0) {
                stmt.executeBatch()
                connection.commit()
                inserted += batchCount
            }
        }

        println("완료: 주문 ${inserted}건 생성 (전부 COMPLETED, 최근 ${HISTORY_DAYS}일에 걸쳐 분산)")
    }
}

/** id 범위를 무작위로 잡고 그 이후 첫 행을 찾는 방식 - ORDER BY RAND() 전체 정렬을 피한다. */
private fun sampleAddresses(connection: Connection, sampleSize: Int): List<AddressSample> {
    val maxId = connection.prepareStatement("SELECT MAX(id) FROM addresses").use { stmt ->
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }
    if (maxId <= 0) return emptyList()

    val result = ArrayList<AddressSample>(sampleSize)
    connection.prepareStatement("SELECT id, user_id FROM addresses WHERE id >= ? ORDER BY id LIMIT 1").use { stmt ->
        repeat(sampleSize) {
            stmt.setLong(1, Random.nextLong(1, maxId + 1))
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    result.add(AddressSample(rs.getLong("id"), rs.getLong("user_id")))
                }
            }
        }
    }
    return result
}

private fun fetchAllRiderIds(connection: Connection): List<Long> {
    val result = ArrayList<Long>()
    connection.prepareStatement("SELECT id FROM riders").use { stmt ->
        stmt.executeQuery().use { rs ->
            while (rs.next()) {
                result.add(rs.getLong("id"))
            }
        }
    }
    return result
}

private fun sampleActiveMerchants(connection: Connection, sampleSize: Int): List<Long> {
    val maxId = connection.prepareStatement("SELECT MAX(id) FROM merchants").use { stmt ->
        stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }
    if (maxId <= 0) return emptyList()

    val result = ArrayList<Long>(sampleSize)
    connection.prepareStatement(
        "SELECT id FROM merchants WHERE business_status_code = '01' AND id >= ? ORDER BY id LIMIT 1"
    ).use { stmt ->
        repeat(sampleSize) {
            stmt.setLong(1, Random.nextLong(1, maxId + 1))
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    result.add(rs.getLong("id"))
                }
            }
        }
    }
    return result
}
