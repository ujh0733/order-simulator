package dev.junghun.ordersimulator.order

import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

data class OrderHistoryItem(
    val id: Long,
    val userName: String,
    val userPhone: String,
    val merchantName: String,
    val regionName: String,
    val riderName: String?,
    val status: String,
    val estimatedDeliveryMinutes: Int,
    val orderedAt: LocalDateTime,
    val dispatchedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)

data class OrderHistoryPageResponse(
    val content: List<OrderHistoryItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@RestController
@RequestMapping("/api/orders")
class OrderHistoryController(
    private val orderRepository: OrderRepository,
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) regionId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): OrderHistoryPageResponse {
        val result = orderRepository.search(
            status = status,
            regionId = regionId,
            from = from?.atStartOfDay(),
            to = to?.plusDays(1)?.atStartOfDay(),
            keyword = keyword?.trim()?.ifBlank { null },
            pageable = PageRequest.of(page, size),
        )

        return OrderHistoryPageResponse(
            content = result.content.map {
                OrderHistoryItem(
                    id = it.id!!,
                    userName = it.user.name,
                    userPhone = it.user.phone,
                    merchantName = it.merchant.name,
                    regionName = it.address.region.name,
                    riderName = it.rider?.name,
                    status = it.status.name,
                    estimatedDeliveryMinutes = it.estimatedDeliveryMinutes,
                    orderedAt = it.orderedAt,
                    dispatchedAt = it.dispatchedAt,
                    completedAt = it.completedAt,
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}
