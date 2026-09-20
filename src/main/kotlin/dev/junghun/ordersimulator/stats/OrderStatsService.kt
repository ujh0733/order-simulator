package dev.junghun.ordersimulator.stats

import dev.junghun.ordersimulator.order.OrderRepository
import dev.junghun.ordersimulator.region.RegionLevel
import dev.junghun.ordersimulator.region.RegionRepository
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate

enum class StatsUnit {
    DAY, WEEK, MONTH, YEAR, CUSTOM
}

data class MerchantRegionCount(
    val regionName: String,
    val count: Long,
)

data class RegionCountResponse(
    val regionId: Long,
    val regionName: String,
    val externalCode: String?,
    val count: Long,
    // hover 시 보여줄 범례: 이 지역 "주문자"들이 어느 지역 "가게"에서 시켰는지 breakdown.
    val byMerchantRegion: List<MerchantRegionCount>,
)

@Service
class OrderStatsService(
    private val orderRepository: OrderRepository,
    private val regionRepository: RegionRepository,
) {

    /** sidoName 하위 GU 지역들의, 주어진 기간 내 "주문자 기준" 건수를 모두 반환한다(0건인 지역도 포함). */
    fun getRegionCounts(
        unit: StatsUnit,
        from: LocalDate?,
        to: LocalDate?,
        sidoName: String,
    ): List<RegionCountResponse> {
        val (rangeFrom, rangeTo) = resolveRange(unit, from, to)
        val fromDateTime = rangeFrom.atStartOfDay()
        val toDateTime = rangeTo.atStartOfDay()

        val sido = regionRepository.findByLevelAndName(RegionLevel.SIDO, sidoName)
            ?: throw IllegalArgumentException("존재하지 않는 시/도입니다: $sidoName")
        val guList = regionRepository.findByLevelAndParentId(RegionLevel.GU, sido.id!!)
        val guNameById = guList.associate { it.id to it.name }

        val totalCountByRegionId = orderRepository.countByCustomerRegionBetween(fromDateTime, toDateTime)
            .associate { it.getRegionId() to it.getOrderCount() }

        val breakdownByCustomerRegionId = orderRepository
            .countByCustomerAndMerchantRegionBetween(fromDateTime, toDateTime)
            .groupBy { it.getCustomerRegionId() }

        return guList.map { region ->
            val breakdown = breakdownByCustomerRegionId[region.id].orEmpty()
                .map { pair ->
                    MerchantRegionCount(
                        regionName = guNameById[pair.getMerchantRegionId()] ?: "기타",
                        count = pair.getOrderCount(),
                    )
                }
                .sortedByDescending { it.count }

            RegionCountResponse(
                regionId = region.id!!,
                regionName = region.name,
                externalCode = region.externalCode,
                count = totalCountByRegionId[region.id] ?: 0L,
                byMerchantRegion = breakdown,
            )
        }
    }

    private fun resolveRange(unit: StatsUnit, from: LocalDate?, to: LocalDate?): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (unit) {
            StatsUnit.DAY -> today to today.plusDays(1)
            StatsUnit.WEEK -> {
                val monday = today.with(DayOfWeek.MONDAY)
                monday to monday.plusWeeks(1)
            }

            StatsUnit.MONTH -> {
                val first = today.withDayOfMonth(1)
                first to first.plusMonths(1)
            }

            StatsUnit.YEAR -> {
                val first = today.withDayOfYear(1)
                first to first.plusYears(1)
            }

            StatsUnit.CUSTOM -> {
                val rangeFrom = requireNotNull(from) { "from 날짜가 필요합니다." }
                val rangeTo = requireNotNull(to) { "to 날짜가 필요합니다." }
                rangeFrom to rangeTo.plusDays(1)
            }
        }
    }
}
