package dev.junghun.ordersimulator.order

import dev.junghun.ordersimulator.merchant.MerchantRepository
import dev.junghun.ordersimulator.rider.RiderRepository
import dev.junghun.ordersimulator.rider.RiderStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문의 접수 -> 조리 -> 배차 -> 완료 전이를 담당한다.
 * 각 전이는 [OrderStatusEventRepository]에 이력을 남긴 뒤 orders.status를 갱신한다 -
 * 별도의 통계 집계 갱신 작업은 없다. orders.status가 곧 최신 상태라 대시보드 통계 쿼리가
 * 다음 조회 시 바로 반영해서 읽어간다(라이브 집계, 캐시 없음).
 */
@Service
class OrderLifecycleService(
    private val orderRepository: OrderRepository,
    private val merchantRepository: MerchantRepository,
    private val riderRepository: RiderRepository,
    private val orderStatusEventRepository: OrderStatusEventRepository,
) {

    /** 가게에 조리 여유(용량)가 있으면, 대기 중인 주문 중 가장 오래된 것을 조리 시작한다. */
    @Transactional
    fun startCookingIfCapacityAvailable(merchantId: Long) {
        val merchant = merchantRepository.findById(merchantId).orElse(null) ?: return

        val cookingCount = orderRepository.countByMerchantIdAndStatus(merchantId, OrderStatus.COOKING)
        if (cookingCount >= merchant.maxConcurrentCooking) return

        val next = orderRepository.findFirstByMerchantIdAndStatusOrderByOrderedAtAsc(merchantId, OrderStatus.RECEIVED)
            ?: return

        val now = LocalDateTime.now()
        next.cookingStartedAt = now
        next.cookingReadyAt = now.plusMinutes(merchant.cookingMinutes.toLong())
        transitionTo(next, OrderStatus.COOKING)
    }

    /** 조리가 끝난 주문을 라이더 풀에서 하나 뽑아 배차한다. 가용 라이더가 없으면 다음 tick에 재시도한다. */
    @Transactional
    fun tryDispatch(orderId: Long) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        if (order.status != OrderStatus.COOKING) return

        val rider = riderRepository.findRandomAvailable() ?: riderRepository.findFirstAvailable() ?: return
        // 고른 시점과 잡는 시점 사이에 다른 트랜잭션이 먼저 채갔을 수 있다(특히 tick()이 겹쳐 돌 때).
        // "AVAILABLE일 때만 BUSY로" 원자적 UPDATE로 확정하고, 실패(0건)하면 이번 시도는 포기한다.
        if (riderRepository.claimIfAvailable(rider.id!!) == 0) return

        order.rider = rider
        order.dispatchedAt = LocalDateTime.now()
        transitionTo(order, OrderStatus.DISPATCHED)
    }

    /** 배달이 끝난 주문을 완료 처리하고, 물고 있던 라이더를 다시 가용 상태로 돌려놓는다. */
    @Transactional
    fun completeOrder(orderId: Long) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        if (order.status != OrderStatus.DISPATCHED) return

        order.rider?.status = RiderStatus.AVAILABLE
        order.completedAt = LocalDateTime.now()
        transitionTo(order, OrderStatus.COMPLETED)
    }

    private fun transitionTo(order: Order, to: OrderStatus) {
        orderStatusEventRepository.save(
            OrderStatusEvent(order = order, fromStatus = order.status, toStatus = to)
        )
        order.status = to
    }
}
