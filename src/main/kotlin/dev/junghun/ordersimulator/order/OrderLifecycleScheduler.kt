package dev.junghun.ordersimulator.order

import dev.junghun.ordersimulator.rider.RiderRepository
import dev.junghun.ordersimulator.rider.RiderStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 접수 -> 조리 -> 배차 -> 완료 파이프라인을 주기적으로 돌린다.
 * 조리 시간은 가게마다 다르다(merchants.cooking_minutes) - 주문이 COOKING으로 넘어갈 때
 * 이미 cookingReadyAt으로 계산해뒀기 때문에 여기서는 그 시각이 지났는지만 보면 된다.
 */
@Component
class OrderLifecycleScheduler(
    private val orderRepository: OrderRepository,
    private val riderRepository: RiderRepository,
    private val orderLifecycleService: OrderLifecycleService,
) {

    @Scheduled(fixedRate = TICK_MILLIS)
    fun tick() {
        startCookingWhereCapacityAllows()
        dispatchReadyOrders()
        completeDeliveredOrders()
    }

    /** 대기 중인 주문이 있는 가게만 골라 용량을 체크한다(가게 전체를 훑지 않는다). */
    private fun startCookingWhereCapacityAllows() {
        orderRepository.findDistinctMerchantIdsByStatus(OrderStatus.RECEIVED)
            .forEach { orderLifecycleService.startCookingIfCapacityAvailable(it) }
    }

    /**
     * 라이더가 남아있는 만큼만 배차를 시도한다. 라이더가 다 찼는데도 조리 끝난 주문마다
     * 개별 트랜잭션으로 배차를 시도하면(실패할 걸 알면서도) 커넥션 풀만 소모해서
     * 다른 작업(주문 생성 등)까지 느려지는 걸 실측으로 확인했다.
     */
    private fun dispatchReadyOrders() {
        val availableRiders = riderRepository.countByStatus(RiderStatus.AVAILABLE).toInt()
        if (availableRiders <= 0) return

        orderRepository.findByStatusAndCookingReadyAtLessThanEqual(OrderStatus.COOKING, LocalDateTime.now())
            .take(availableRiders)
            .forEach { orderLifecycleService.tryDispatch(it.id!!) }
    }

    /** 배차 중(DISPATCHED)인 주문은 라이더 풀 크기(50명)로 자연히 제한되니 전량 조회 후 필터링해도 된다. */
    private fun completeDeliveredOrders() {
        val now = LocalDateTime.now()
        orderRepository.findByStatus(OrderStatus.DISPATCHED)
            .filter { it.dispatchedAt != null && it.dispatchedAt!!.plusMinutes(it.estimatedDeliveryMinutes.toLong()) <= now }
            .forEach { orderLifecycleService.completeOrder(it.id!!) }
    }

    companion object {
        private const val TICK_MILLIS = 5_000L
    }
}
