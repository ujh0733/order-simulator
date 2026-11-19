package dev.junghun.ordersimulator.order

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 접수 -> 조리 -> 배차 -> 완료 파이프라인을 주기적으로 돌린다.
 * 조리 시간은 가게마다 다르다(merchants.cooking_minutes) - 주문이 COOKING으로 넘어갈 때
 * 이미 cookingReadyAt으로 계산해뒀기 때문에 여기서는 그 시각이 지났는지만 보면 된다.
 *
 * 단계마다 한 tick에 처리하는 건수에 상한(BATCH_LIMIT)을 둔다. 상한이 없으면 백로그가 쌓였을 때
 * 앞 단계 하나가 tick 전체를 몇 분씩 붙잡고, 그동안 뒤 단계(특히 완료)는 한 번도 돌지 못한다.
 */
@Component
class OrderLifecycleScheduler(
    private val orderLifecycleService: OrderLifecycleService,
) {

    @Scheduled(fixedRate = TICK_MILLIS)
    fun tick() {
        // 단계별로 몇 건을 얼마 동안 처리했는지 남겨서 tick 구조를 바꾸기 전후를 비교한다.
        val tickStarted = System.nanoTime()
        val cooking = timed { orderLifecycleService.startCookingBatch(BATCH_LIMIT) }
        val dispatch = timed { orderLifecycleService.dispatchBatch(BATCH_LIMIT) }
        val complete = timed { orderLifecycleService.completeBatch(BATCH_LIMIT) }

        if (cooking.scanned + dispatch.scanned + complete.scanned == 0) return
        log.info(
            "lifecycle tick: cooking={}/{}건 {}ms, dispatch={}/{}건 {}ms, complete={}/{}건 {}ms, total={}ms",
            cooking.transitioned, cooking.scanned, cooking.millis,
            dispatch.transitioned, dispatch.scanned, dispatch.millis,
            complete.transitioned, complete.scanned, complete.millis,
            elapsedMillis(tickStarted),
        )
    }

    private inline fun timed(block: () -> TransitionBatch): PhaseResult {
        val started = System.nanoTime()
        val batch = block()
        return PhaseResult(batch.scanned, batch.transitioned, elapsedMillis(started))
    }

    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    private data class PhaseResult(val scanned: Int, val transitioned: Int, val millis: Long)

    companion object {
        private const val TICK_MILLIS = 5_000L
        private const val BATCH_LIMIT = 1_000
        private val log = LoggerFactory.getLogger(OrderLifecycleScheduler::class.java)
    }
}
