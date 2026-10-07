package dev.junghun.ordersimulator.simulation

import dev.junghun.ordersimulator.address.RandomAddressPicker
import dev.junghun.ordersimulator.merchant.ActiveMerchantPool
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

@Service
class LoadGenerationService(
    private val state: LoadGenerationState,
    private val loadGenerationJobRepository: LoadGenerationJobRepository,
    private val activeMerchantPool: ActiveMerchantPool,
    private val randomAddressPicker: RandomAddressPicker,
) {

    /** count건을 durationMinutes분 동안, 시간축 위에 무작위로 흩뿌려 생성하도록 예약한다. */
    @Transactional
    fun start(count: Int, durationMinutes: Int) {
        require(count > 0) { "생성할 건수는 1 이상이어야 합니다." }
        require(durationMinutes > 0) { "생성 시간은 1분 이상이어야 합니다." }
        check(!state.running) { "이미 주문 생성이 진행 중입니다." }
        activeMerchantPool.reload()
        randomAddressPicker.reload()

        val startedAt = Instant.now()
        val durationSeconds = durationMinutes * 60L
        // 1분 단위로 균등 분배하는 게 아니라, 전체 구간에서 주문 하나하나의 발생 시각을 무작위로 뽑는다.
        val offsets = LongArray(count) { Random.nextLong(0, durationSeconds) }
        offsets.sort()

        val job = loadGenerationJobRepository.save(
            LoadGenerationJob(
                total = count,
                completed = 0,
                durationMinutes = durationMinutes,
                startedAt = toLocalDateTime(startedAt),
                running = true,
            )
        )

        state.total = count
        state.durationMinutes = durationMinutes
        state.offsetsSeconds = offsets
        state.firePointer = 0
        state.completed.set(0)
        state.failedMessage = null
        state.startedAt = startedAt
        state.jobId = job.id
        state.running = true
    }

    /**
     * 서버가 재시작됐을 때, 재시작 전에 돌고 있던 작업이 있으면 남은 건수를 남은 시간에 다시
     * 흩뿌려서 이어간다. offset은 원래 시작 시각(job.startedAt) 기준을 그대로 유지해서
     * 화면에 보이는 "경과 시간"이 재시작 때문에 0으로 되돌아가지 않게 한다.
     */
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun resumeIfNeeded() {
        val job = loadGenerationJobRepository.findFirstByRunningTrueOrderByIdDesc() ?: return

        val remaining = job.total - job.completed
        if (remaining <= 0) {
            job.running = false
            job.endedAt = job.endedAt ?: LocalDateTime.now()
            return
        }
        // 재시작 복구 경로는 start()를 거치지 않으므로 여기서도 가게 풀과 주소 범위를 다시 채운다.
        activeMerchantPool.reload()
        randomAddressPicker.reload()

        val startedAtInstant = job.startedAt.atZone(ZoneId.systemDefault()).toInstant()
        val durationSeconds = job.durationMinutes * 60L
        val elapsedSeconds = Duration.between(startedAtInstant, Instant.now()).seconds.coerceAtLeast(0)
        // 이미 목표 시간을 넘겼으면 남은 건 전부 "지금 당장" 발생하도록 흩뿌린다.
        val upperBound = (elapsedSeconds + 1).coerceAtLeast(durationSeconds)

        val offsets = LongArray(remaining) { Random.nextLong(elapsedSeconds, upperBound) }
        offsets.sort()

        state.total = job.total
        state.durationMinutes = job.durationMinutes
        state.offsetsSeconds = offsets
        state.firePointer = 0
        state.completed.set(job.completed)
        state.failedMessage = job.failedMessage
        state.startedAt = startedAtInstant
        state.jobId = job.id
        state.running = true
    }

    private fun toLocalDateTime(instant: Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
}
