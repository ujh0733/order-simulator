package dev.junghun.ordersimulator.simulation

import dev.junghun.ordersimulator.order.OrderGeneratorService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * 예약된 offset(발생 예정 시각)을 확인해, 이미 지난 것들을 생성한다.
 * 생성 속도가 목표 배분 속도를 못 따라가면(=병목) 자연스럽게 목표 시간을 넘겨서 끝난다 -
 * 강제로 속도를 맞추지 않고, 시스템이 실제로 처리 가능한 만큼만 처리한다.
 *
 * 건마다 generateOrder()를 개별 호출해서 하나씩 즉시 커밋되게 한다 - 밀린 건수를 통째로
 * 한 트랜잭션(generateOrders)으로 묶으면, 그 배치가 다 끝나야만 커밋/진행률 갱신이 되어
 * 화면에는 몇 분간 0%로 멈춘 것처럼 보이는 문제가 있었다.
 */
@Component
class LoadGenerationScheduler(
    private val orderGeneratorService: OrderGeneratorService,
    private val state: LoadGenerationState,
    private val loadGenerationJobRepository: LoadGenerationJobRepository,
) {

    @Scheduled(fixedRate = TICK_MILLIS)
    fun tick() {
        if (!state.running) return

        val startedAt = state.startedAt ?: return
        val offsets = state.offsetsSeconds
        val processedBefore = state.firePointer

        while (state.firePointer < offsets.size) {
            val elapsedSeconds = Duration.between(startedAt, Instant.now()).seconds
            if (offsets[state.firePointer] > elapsedSeconds) break

            try {
                orderGeneratorService.generateOrder()
            } catch (e: Exception) {
                state.failedMessage = e.message ?: "알 수 없는 오류로 생성이 중단되었습니다."
                state.running = false
                persistJobState()
                return
            }
            state.completed.incrementAndGet()
            state.firePointer++
        }

        if (state.firePointer >= offsets.size) {
            state.running = false
        }

        // 매 주문마다 DB에 쓰지 않고 tick(3초)당 한 번만 반영한다 - 재시작 내구성엔 이 정도 정밀도면 충분하다.
        if (state.firePointer != processedBefore || !state.running) {
            persistJobState()
        }
    }

    private fun persistJobState() {
        val jobId = state.jobId ?: return
        // running이 false로 바뀌는 이 시점이 "실제로 끝난 시각"이다. 목표 시간(duration)을 넘겨서
        // 끝났는지는 이 값과 started_at + duration_minutes를 비교하면 알 수 있다.
        val endedAt = if (!state.running) LocalDateTime.now() else null
        loadGenerationJobRepository.updateProgress(jobId, state.completed.get(), state.running, state.failedMessage, endedAt)
    }

    companion object {
        private const val TICK_MILLIS = 3_000L
    }
}
