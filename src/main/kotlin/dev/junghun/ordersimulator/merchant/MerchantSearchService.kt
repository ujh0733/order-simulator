package dev.junghun.ordersimulator.merchant

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate

data class MerchantSearchCondition(
    val sido: String? = null,
    val region: String? = null,
    val businessStatus: String? = null,
    val cookingMinutes: Int? = null,
    val keyword: String? = null,
)

data class MerchantItem(
    val id: Long,
    val name: String,
    val sidoName: String,
    val regionName: String,
    val businessStatusName: String?,
    val roadAddress: String?,
    val phone: String?,
    val licenseDate: LocalDate?,
    val cookingMinutes: Int,
    val maxConcurrentCooking: Int,
    val orderCount: Long,
)

data class MerchantPage(
    val content: List<MerchantItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class FacetOption(val value: String, val label: String, val count: Long)

/** 시/군/구 옵션. sido(시/도 이름)로 어느 시/도 밑인지 알려줘서 화면에서 시/도 선택에 맞춰 걸러 보여준다. */
data class RegionFacetOption(val value: String, val label: String, val count: Long, val sido: String)

data class MerchantFacets(
    val sidos: List<FacetOption>,
    val regions: List<RegionFacetOption>,
    val businessStatuses: List<FacetOption>,
    val cookingMinutes: List<FacetOption>,
)

@Service
class MerchantSearchService(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    fun search(condition: MerchantSearchCondition, page: Int, size: Int): MerchantPage {
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val (where, params) = buildWhere(condition)

        // 개수 조회에는 조인이 필요 없다. 주문 내역에서 COUNT에 조인이 딸려 붙어 수 분이 걸린 적이 있어서,
        // 지역명 조인은 잘라낸 페이지(최대 100건)에만 붙인다.
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM merchants m $where", params, Long::class.java) ?: 0L

        params.addValue("limit", safeSize)
        params.addValue("offset", safePage.toLong() * safeSize)
        // 가게별 주문수는 지금은 orders를 매번 집계해서 구한다. 잘라낸 페이지(최대 100건)에 대해서만
        // 가게당 서브쿼리 1번이라 지금 규모에서는 문제없다. 이후 느려지면 그때 merchants에 카운트
        // 컬럼을 추가해서 갱신하는 방식으로 바꾼다.
        val content = jdbc.query(
            """
            SELECT m.id, m.name, p.name AS sido_name, r.name AS region_name, m.business_status_name,
                   m.road_address, m.phone, m.license_date, m.cooking_minutes, m.max_concurrent_cooking,
                   (SELECT COUNT(*) FROM orders o WHERE o.merchant_id = m.id) AS order_count
            FROM (
                SELECT * FROM merchants m $where ORDER BY m.id LIMIT :limit OFFSET :offset
            ) m
            JOIN regions r ON r.id = m.region_id
            JOIN regions p ON p.id = r.parent_id
            ORDER BY m.id
            """.trimIndent(),
            params,
        ) { rs, _ ->
            MerchantItem(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                sidoName = rs.getString("sido_name"),
                regionName = rs.getString("region_name"),
                businessStatusName = rs.getString("business_status_name"),
                roadAddress = rs.getString("road_address"),
                phone = rs.getString("phone"),
                licenseDate = rs.getDate("license_date")?.toLocalDate(),
                cookingMinutes = rs.getInt("cooking_minutes"),
                maxConcurrentCooking = rs.getInt("max_concurrent_cooking"),
                orderCount = rs.getLong("order_count"),
            )
        }

        return MerchantPage(
            content = content,
            page = safePage,
            size = safeSize,
            totalElements = total,
            totalPages = ((total + safeSize - 1) / safeSize).toInt(),
        )
    }

    /**
     * 카테고리별 전체 건수. 현재 조건과 무관하게 전체 가게 기준이다.
     * 지역은 regions 테이블에서 시작해 가게를 LEFT JOIN 하므로, 가게가 없는 지역도 0건으로 나온다.
     */
    fun facets(): MerchantFacets = MerchantFacets(
        sidos = jdbc.query(
            """
            SELECT p.name AS id, p.name AS label, COUNT(m.id) AS cnt
            FROM regions p
            JOIN regions r ON r.parent_id = p.id AND r.level = 'GU'
            LEFT JOIN merchants m ON m.region_id = r.id
            WHERE p.level = 'SIDO'
            GROUP BY p.id, p.name
            ORDER BY p.id
            """.trimIndent(),
            ::toFacetOption,
        ),
        regions = jdbc.query(
            """
            SELECT r.name AS id, r.name AS label, COUNT(m.id) AS cnt, p.name AS sido
            FROM regions r
            JOIN regions p ON p.id = r.parent_id AND p.level = 'SIDO'
            LEFT JOIN merchants m ON m.region_id = r.id
            WHERE r.level = 'GU'
            GROUP BY r.id, r.name, p.name
            ORDER BY r.name
            """.trimIndent(),
        ) { rs, _ ->
            RegionFacetOption(rs.getString("id"), rs.getString("label"), rs.getLong("cnt"), rs.getString("sido"))
        },
        businessStatuses = jdbc.query(
            """
            SELECT business_status_name AS id, business_status_name AS label, COUNT(*) AS cnt
            FROM merchants
            WHERE business_status_name IS NOT NULL
            GROUP BY business_status_name
            ORDER BY cnt DESC
            """.trimIndent(),
            ::toFacetOption,
        ),
        cookingMinutes = jdbc.query(
            """
            SELECT cooking_minutes AS id, CONCAT(cooking_minutes, '분') AS label, COUNT(*) AS cnt
            FROM merchants
            GROUP BY cooking_minutes
            ORDER BY cooking_minutes
            """.trimIndent(),
            ::toFacetOption,
        ),
    )

    private fun toFacetOption(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) =
        FacetOption(rs.getString("id"), rs.getString("label"), rs.getLong("cnt"))

    // 조건 조각은 전부 고정 문자열이고 값은 바인딩 파라미터로만 들어간다(SQL 인젝션 방지).
    private fun buildWhere(c: MerchantSearchCondition): Pair<String, MapSqlParameterSource> {
        val params = MapSqlParameterSource()
        val clauses = mutableListOf<String>()

        // 시/도 이름, 시/군/구 이름으로 대상 구를 찾는다. 구 이름은 시/도 간에 겹칠 수 있어(중구, 강서구 등)
        // 화면은 시/도와 함께 보내고, 시/도만 있으면 그 시/도의 모든 구가 대상이 된다.
        val sido = c.sido?.trim()?.takeIf { it.isNotEmpty() }
        val region = c.region?.trim()?.takeIf { it.isNotEmpty() }
        if (sido != null || region != null) {
            val regionConds = mutableListOf<String>()
            if (sido != null) {
                regionConds += "p.name = :sido"
                params.addValue("sido", sido)
            }
            if (region != null) {
                regionConds += "r.name = :region"
                params.addValue("region", region)
            }
            clauses += "m.region_id IN (SELECT r.id FROM regions r JOIN regions p ON p.id = r.parent_id " +
                "WHERE ${regionConds.joinToString(" AND ")})"
        }
        c.businessStatus?.takeIf { it.isNotBlank() }?.let {
            clauses += "m.business_status_name = :businessStatus"
            params.addValue("businessStatus", it)
        }
        c.cookingMinutes?.let {
            clauses += "m.cooking_minutes = :cookingMinutes"
            params.addValue("cookingMinutes", it)
        }
        c.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
            clauses += """
                (m.name LIKE :keyword OR m.road_address LIKE :keyword
                 OR m.lot_address LIKE :keyword OR m.phone LIKE :keyword)
            """.trimIndent()
            params.addValue("keyword", "%${escapeLike(it)}%")
        }

        val where = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")
        return where to params
    }

    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
