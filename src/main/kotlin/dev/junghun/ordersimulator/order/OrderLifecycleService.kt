package dev.junghun.ordersimulator.order

import dev.junghun.ordersimulator.rider.RandomRiderPicker
import dev.junghun.ordersimulator.rider.RiderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 한 번의 일괄 전이에서 읽어 온 대상 수(scanned)와 실제로 상태를 바꾼 수(transitioned). */
data class TransitionBatch(val scanned: Int, val transitioned: Int) {
    companion object {
        val NONE = TransitionBatch(0, 0)
    }
}

/**
 * 주문의 접수 -> 조리 -> 배차 -> 완료 전이를 담당한다.
 * 각 전이는 [OrderStatusEventRepository]에 이력을 남긴 뒤 orders.status를 갱신한다 -
 * 별도의 통계 집계 갱신 작업은 없다. orders.status가 곧 최신 상태라 대시보드 통계 쿼리가
 * 다음 조회 시 바로 반영해서 읽어간다(라이브 집계, 캐시 없음).
 *
 * 예전에는 가게/주문 1건마다 트랜잭션을 따로 열어 조회 -> COUNT -> 갱신 -> 커밋을 반복했는데,
 * 백로그가 쌓이면 tick 하나가 수십 초씩 걸렸다(가게 1곳당 약 9ms, 그중 커밋이 약 1.2ms).
 * 지금은 단계마다 limit건을 한 트랜잭션에서 묶어 처리하고, 가게별 정보와 건수는 한 번에 읽는다.
 */
@Service
class OrderLifecycleService(
    private val orderRepository: OrderRepository,
    private val riderRepository: RiderRepository,
    private val randomRiderPicker: RandomRiderPicker,
    private val orderStatusEventRepository: OrderStatusEventRepository,
) {

    /** 대기 중인 주문을 오래된 순으로 limit건 읽어, 가게마다 남은 조리 용량만큼 조리를 시작한다. */
    @Transactional
    fun startCookingBatch(limit: Int): TransitionBatch {
        val received = orderRepository.findOldestWithMerchantByStatus(OrderStatus.RECEIVED, PageRequest.of(0, limit))
        if (received.isEmpty()) return TransitionBatch.NONE

        val byMerchant = received.groupBy { it.merchant }
        val cookingCounts = orderRepository
            .countByMerchantIdsAndStatus(byMerchant.keys.map { it.id!! }, OrderStatus.COOKING)
            .associate { it.getMerchantId() to it.getOrderCount() }

        val now = LocalDateTime.now()
        var started = 0
        byMerchant.forEach { (merchant, orders) ->
            val freeSlots = merchant.maxConcurrentCooking - (cookingCounts[merchant.id] ?: 0L).toInt()
            orders.take(freeSlots.coerceAtLeast(0)).forEach { order ->
                order.cookingStartedAt = now
                order.cookingReadyAt = now.plusMinutes(merchant.cookingMinutes.toLong())
                transitionTo(order, OrderStatus.COOKING)
                started++
            }
        }
        return TransitionBatch(scanned = received.size, transitioned = started)
    }

    /** 조리가 끝난 주문을 먼저 준비된 순으로 limit건 읽어, 가용 라이더가 있는 만큼 배차한다. */
    @Transactional
    fun dispatchBatch(limit: Int): TransitionBatch {
        val ready = orderRepository.findReadyToDispatch(OrderStatus.COOKING, LocalDateTime.now(), PageRequest.of(0, limit))
        if (ready.isEmpty()) return TransitionBatch.NONE

        // 라이더가 다 찼는데도 배차를 시도하면 실패할 UPDATE만 반복하게 되므로 먼저 센다.
        // 배차할 주문 수까지만 세면 된다 - 가용 라이더 전체(약 37만 명)를 세지 않는다.
        val availableRiders = riderRepository.countAvailableUpTo(ready.size).toInt()

        val now = LocalDateTime.now()
        var dispatched = 0
        for (order in ready.take(availableRiders)) {
            // 무작위로 고른 라이더를 "AVAILABLE일 때만 BUSY로" 원자적 UPDATE로 확정한다.
            // 확정하지 못했다는 건 가용 라이더가 바닥났다는 뜻이라 나머지는 다음 tick에 넘긴다.
            val riderId = randomRiderPicker.claimRandomAvailable() ?: break
            order.rider = riderRepository.getReferenceById(riderId)
            order.dispatchedAt = now
            transitionTo(order, OrderStatus.DISPATCHED)
            dispatched++
        }
        return TransitionBatch(scanned = ready.size, transitioned = dispatched)
    }

    /** 배달 예상시간이 지난 주문을 limit건 완료 처리하고, 물고 있던 라이더를 한 번에 가용 상태로 돌린다. */
    @Transactional
    fun completeBatch(limit: Int): TransitionBatch {
        val now = LocalDateTime.now()
        val delivered = orderRepository.findDelivered(now, limit)
        if (delivered.isEmpty()) return TransitionBatch.NONE

        val riderIds = delivered.mapNotNull { it.rider?.id }
        if (riderIds.isNotEmpty()) riderRepository.releaseAll(riderIds)

        delivered.forEach { order ->
            order.completedAt = now
            transitionTo(order, OrderStatus.COMPLETED)
        }
        return TransitionBatch(scanned = delivered.size, transitioned = delivered.size)
    }

    private fun transitionTo(order: Order, to: OrderStatus) {
        orderStatusEventRepository.save(
            OrderStatusEvent(order = order, fromStatus = order.status, toStatus = to)
        )
        order.status = to
    }
}
