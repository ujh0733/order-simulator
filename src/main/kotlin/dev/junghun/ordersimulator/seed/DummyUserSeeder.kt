package dev.junghun.ordersimulator.seed

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * 개발용 MySQL(docker-compose)에 더미 고객/주소 데이터를 대량 생성한다.
 * JPA 엔티티 없이 순수 JDBC 배치insert로 처리한다 - 50만 건 규모를 Hibernate로 한 건씩 넣으면
 * 너무 느리고, 이 스크립트는 스키마 검증(ddl-auto=validate)과 무관한 1회성 데이터 적재용이기 때문이다.
 */
private const val JDBC_URL =
    "jdbc:mysql://localhost:3306/order_simulator?characterEncoding=UTF-8&serverTimezone=Asia/Seoul" +
        "&allowPublicKeyRetrieval=true&useSSL=false&rewriteBatchedStatements=true"
private const val DB_USER = "app"
private const val DB_PASSWORD = "app"

private const val USER_COUNT = 500_000
private const val CHUNK_SIZE = 2_000

private val SURNAMES = listOf(
    "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
    "한", "오", "서", "신", "권", "황", "안", "송", "전", "홍",
)
private val GIVEN_NAMES = listOf(
    "민준", "서연", "도윤", "하은", "시우", "지우", "예준", "수아", "지호", "채원",
    "준서", "지민", "현우", "다은", "우진", "서윤", "건우", "유나", "선우", "예은",
)
private val ROAD_NAMES = listOf(
    "테헤란로", "강남대로", "올림픽로", "월드컵로", "가로수길", "성수이로", "홍제천로",
    "동작대로", "여의대로", "노해로", "도산대로", "천호대로", "화랑로", "북부간선도로",
)

private val START_EPOCH_SECOND = LocalDateTime.of(2020, 1, 1, 0, 0).toEpochSecond(ZoneOffset.of("+09:00"))
private val END_EPOCH_SECOND = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+09:00"))

fun main() {
    Class.forName("com.mysql.cj.jdbc.Driver")
    DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD).use { connection ->
        connection.autoCommit = false

        val guRegions = fetchGuRegions(connection)
        check(guRegions.isNotEmpty()) { "GU 지역 데이터가 없습니다. regions 마이그레이션(V2)이 적용됐는지 확인하세요." }

        println("자치구 ${guRegions.size}개 확인. 유저 ${USER_COUNT}명 생성을 시작합니다.")

        val usedPhones = HashSet<String>(USER_COUNT * 2)
        var totalUsers = 0
        var totalAddresses = 0

        var remaining = USER_COUNT
        while (remaining > 0) {
            val chunk = minOf(CHUNK_SIZE, remaining)
            val userIds = insertUserChunk(connection, chunk, usedPhones)
            totalAddresses += insertAddressChunk(connection, userIds, guRegions)
            connection.commit()

            totalUsers += chunk
            remaining -= chunk
            if (totalUsers % 50_000 == 0 || remaining == 0) {
                println("진행: 유저 ${totalUsers}명 / 주소 ${totalAddresses}건 생성 완료")
            }
        }

        println("완료: 유저 ${totalUsers}명, 주소 ${totalAddresses}건 생성")
    }
}

private fun fetchGuRegions(connection: Connection): List<Pair<Long, String>> {
    connection.prepareStatement("SELECT id, name FROM regions WHERE level = 'GU'").use { stmt ->
        stmt.executeQuery().use { rs ->
            val result = mutableListOf<Pair<Long, String>>()
            while (rs.next()) {
                result.add(rs.getLong("id") to rs.getString("name"))
            }
            return result
        }
    }
}

/** 유저 chunk건을 insert하고, 생성된 내부 PK 목록을 반환한다(주소 생성 시 FK로 사용). */
private fun insertUserChunk(connection: Connection, chunk: Int, usedPhones: MutableSet<String>): List<Long> {
    val sql = "INSERT INTO users (user_id, name, phone, email, created_at) VALUES (?, ?, ?, ?, ?)"
    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
        repeat(chunk) {
            stmt.setString(1, UUID.randomUUID().toString())
            stmt.setString(2, randomName())
            stmt.setString(3, randomUniquePhone(usedPhones))
            stmt.setNull(4, Types.VARCHAR)
            stmt.setTimestamp(5, Timestamp.valueOf(randomDateTimeSince2020()))
            stmt.addBatch()
        }
        stmt.executeBatch()

        val generatedIds = mutableListOf<Long>()
        stmt.generatedKeys.use { keys ->
            while (keys.next()) {
                generatedIds.add(keys.getLong(1))
            }
        }
        return generatedIds
    }
}

/** 유저별로 1~3개의 주소를 생성한다. 주소 생성일시는 해당 유저의 가입일시 이후로 맞춘다. */
private fun insertAddressChunk(
    connection: Connection,
    userIds: List<Long>,
    guRegions: List<Pair<Long, String>>,
): Int {
    val userCreatedAtById = fetchUserCreatedAt(connection, userIds)

    val sql = """
        INSERT INTO addresses (user_id, region_id, road_address, lot_address, detail_address, zip_code, is_default, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    var addressCount = 0
    connection.prepareStatement(sql).use { stmt ->
        for (userId in userIds) {
            val userCreatedAt = userCreatedAtById.getValue(userId)
            val addressCountForUser = Random.nextInt(1, 4)

            repeat(addressCountForUser) { index ->
                val (regionId, regionName) = guRegions.random()
                stmt.setLong(1, userId)
                stmt.setLong(2, regionId)
                stmt.setString(3, randomRoadAddress(regionName))
                stmt.setString(4, randomLotAddress(regionName))
                stmt.setString(5, randomDetailAddress())
                stmt.setString(6, randomZipCode())
                stmt.setBoolean(7, index == 0)
                stmt.setTimestamp(8, Timestamp.valueOf(randomDateTimeAfter(userCreatedAt)))
                stmt.addBatch()
                addressCount++
            }
        }
        stmt.executeBatch()
    }
    return addressCount
}

private fun fetchUserCreatedAt(connection: Connection, userIds: List<Long>): Map<Long, LocalDateTime> {
    if (userIds.isEmpty()) return emptyMap()
    val placeholders = userIds.joinToString(",") { "?" }
    connection.prepareStatement("SELECT id, created_at FROM users WHERE id IN ($placeholders)").use { stmt ->
        userIds.forEachIndexed { index, id -> stmt.setLong(index + 1, id) }
        stmt.executeQuery().use { rs ->
            val result = HashMap<Long, LocalDateTime>(userIds.size * 2)
            while (rs.next()) {
                result[rs.getLong("id")] = rs.getTimestamp("created_at").toLocalDateTime()
            }
            return result
        }
    }
}

private fun randomName(): String = SURNAMES.random() + GIVEN_NAMES.random()

private fun randomUniquePhone(used: MutableSet<String>): String {
    while (true) {
        val candidate = "010%08d".format(Random.nextInt(0, 100_000_000))
        if (used.add(candidate)) return candidate
    }
}

private fun randomDateTimeSince2020(): LocalDateTime {
    val randomEpochSecond = Random.nextLong(START_EPOCH_SECOND, END_EPOCH_SECOND)
    return LocalDateTime.ofEpochSecond(randomEpochSecond, 0, ZoneOffset.of("+09:00"))
}

private fun randomDateTimeAfter(from: LocalDateTime): LocalDateTime {
    val fromEpoch = from.toEpochSecond(ZoneOffset.of("+09:00"))
    if (fromEpoch >= END_EPOCH_SECOND) return from
    val randomEpochSecond = Random.nextLong(fromEpoch, END_EPOCH_SECOND)
    return LocalDateTime.ofEpochSecond(randomEpochSecond, 0, ZoneOffset.of("+09:00"))
}

private fun randomRoadAddress(regionName: String): String =
    "$regionName ${ROAD_NAMES.random()} ${Random.nextInt(1, 300)}"

private fun randomLotAddress(regionName: String): String =
    "$regionName ${Random.nextInt(1, 999)}-${Random.nextInt(1, 99)}"

private fun randomDetailAddress(): String =
    "${Random.nextInt(1, 25)}동 ${Random.nextInt(101, 2500)}호"

private fun randomZipCode(): String = "%05d".format(Random.nextInt(10000, 63100))
