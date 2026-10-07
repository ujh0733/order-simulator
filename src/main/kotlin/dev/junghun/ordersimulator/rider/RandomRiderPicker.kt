package dev.junghun.ordersimulator.rider

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 무작위 id를 앱에서 한 번만 뽑고, "AVAILABLE일 때만 BUSY로" 원자적 UPDATE로 확정한다.
 * 존재하지 않는 id나 이미 BUSY인 라이더는 UPDATE가 0건이라 같은 방식으로 다시 뽑는다.
 */
@Component
class RandomRiderPicker(
    private val riderRepository: RiderRepository,
) {

    @Volatile
    private var maxId: Long = 0

    fun reload() {
        maxId = riderRepository.findMaxId() ?: 0
    }

    /** 가용 라이더 한 명을 BUSY로 확정하고 그 id를 돌려준다. 확정하지 못하면 null. */
    fun claimRandomAvailable(): Long? {
        if (maxId <= 0) reload()
        val max = maxId
        if (max <= 0) return null

        repeat(MAX_ATTEMPTS) {
            val id = Random.nextLong(1, max + 1)
            if (riderRepository.claimIfAvailable(id) > 0) return id
        }

        // 가용 라이더 비율이 아주 낮아 무작위로 못 찾을 때만, 가장 앞의 가용 라이더로 폴백해서 배차가 멈추지 않게 한다.
        val first = riderRepository.findFirstAvailable()?.id ?: return null
        return if (riderRepository.claimIfAvailable(first) > 0) first else null
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
