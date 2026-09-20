package dev.junghun.ordersimulator.simulation

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * "N건을 M분 동안 생성" 부하 테스트 작업의 상태.
 * offsets[i] = i번째 주문이 시작 시각으로부터 몇 초 뒤에 발생해야 하는지(전체 구간에 균등 분할이 아니라
 * 무작위로 흩뿌린 값). 오름차순으로 정렬해두고 firePointer로 "아직 발사 안 한 것 중 가장 이른 것"을 가리킨다.
 */
@Component
class LoadGenerationState {
    @Volatile
    var total: Int = 0

    @Volatile
    var durationMinutes: Int = 0

    @Volatile
    var startedAt: Instant? = null

    @Volatile
    var running: Boolean = false

    @Volatile
    var failedMessage: String? = null

    @Volatile
    var offsetsSeconds: LongArray = LongArray(0)

    @Volatile
    var firePointer: Int = 0

    val completed = AtomicInteger(0)

    // 이 상태를 거울처럼 비추는 DB 로우의 id. 서버 재시작 시 이 로우를 보고 이어서 시작한다.
    @Volatile
    var jobId: Long? = null
}
