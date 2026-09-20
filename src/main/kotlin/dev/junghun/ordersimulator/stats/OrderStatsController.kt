package dev.junghun.ordersimulator.stats

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/stats")
class OrderStatsController(
    private val orderStatsService: OrderStatsService,
) {

    @GetMapping("/orders")
    fun getOrderCounts(
        @RequestParam(defaultValue = "DAY") unit: StatsUnit,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "서울특별시") sido: String,
    ): List<RegionCountResponse> = orderStatsService.getRegionCounts(unit, from, to, sido)
}
