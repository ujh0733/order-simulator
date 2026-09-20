package dev.junghun.ordersimulator.pipeline

import dev.junghun.ordersimulator.order.OrderRepository
import dev.junghun.ordersimulator.order.OrderStatusEventRepository
import dev.junghun.ordersimulator.order.OrderStatus
import dev.junghun.ordersimulator.rider.RiderRepository
import dev.junghun.ordersimulator.rider.RiderStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class PipelineStatusResponse(
    val received: Long,
    val cooking: Long,
    val dispatched: Long,
    val completed: Long,
    val ridersAvailable: Long,
    val ridersBusy: Long,
)

data class OrderEventResponse(
    val orderId: Long,
    val merchantName: String,
    val riderName: String?,
    val fromStatus: String?,
    val toStatus: String,
    val occurredAt: LocalDateTime,
)

data class DispatchResponse(
    val orderId: Long,
    val riderName: String,
    val merchantName: String,
    val regionName: String,
    val dispatchedAt: LocalDateTime,
    val estimatedDeliveryMinutes: Int,
)

@RestController
@RequestMapping("/api/pipeline")
class PipelineController(
    private val orderRepository: OrderRepository,
    private val riderRepository: RiderRepository,
    private val orderStatusEventRepository: OrderStatusEventRepository,
) {

    @GetMapping("/status")
    fun status(): PipelineStatusResponse = PipelineStatusResponse(
        received = orderRepository.countByStatus(OrderStatus.RECEIVED),
        cooking = orderRepository.countByStatus(OrderStatus.COOKING),
        dispatched = orderRepository.countByStatus(OrderStatus.DISPATCHED),
        completed = orderRepository.countByStatus(OrderStatus.COMPLETED),
        ridersAvailable = riderRepository.countByStatus(RiderStatus.AVAILABLE),
        ridersBusy = riderRepository.countByStatus(RiderStatus.BUSY),
    )

    /** 최근 상태 전이 20건 - 접수/조리/배차/완료가 실제로 흘러가는 걸 눈으로 보기 위한 이벤트 피드. */
    @GetMapping("/events")
    fun recentEvents(): List<OrderEventResponse> =
        orderStatusEventRepository.findTop20ByOrderByOccurredAtDesc().map { event ->
            OrderEventResponse(
                orderId = event.order.id!!,
                merchantName = event.order.merchant.name,
                riderName = event.order.rider?.name,
                fromStatus = event.fromStatus?.name,
                toStatus = event.toStatus.name,
                occurredAt = event.occurredAt,
            )
        }

    /** 지금 배송 중인 주문 - 어느 라이더가 어느 배달을 맡았는지. 라이더 풀 크기(50명)로 자연히 제한된다. */
    @GetMapping("/dispatches")
    fun currentDispatches(): List<DispatchResponse> =
        orderRepository.findByStatus(OrderStatus.DISPATCHED)
            .sortedBy { it.dispatchedAt }
            .map { order ->
                DispatchResponse(
                    orderId = order.id!!,
                    riderName = order.rider?.name ?: "미배정",
                    merchantName = order.merchant.name,
                    regionName = order.address.region.name,
                    dispatchedAt = order.dispatchedAt!!,
                    estimatedDeliveryMinutes = order.estimatedDeliveryMinutes,
                )
            }
}
