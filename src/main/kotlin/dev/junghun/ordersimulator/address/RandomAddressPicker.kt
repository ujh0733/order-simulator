package dev.junghun.ordersimulator.address

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 주소 id는 1부터 MAX(id)까지 연속이라, 난수를 앱에서 한 번만 뽑아 PK로 조회하면 정확히 균등하다.
 * 목록을 메모리에 들 필요 없이 MAX(id) 하나만 기억한다.
 */
@Component
class RandomAddressPicker(
    private val addressRepository: AddressRepository,
) {

    @Volatile
    private var maxId: Long = 0

    fun reload() {
        maxId = addressRepository.findMaxId() ?: 0
    }

    fun pick(): Address {
        if (maxId <= 0) reload()
        val max = maxId
        check(max > 0) { "생성된 배달주소가 없습니다. 더미 유저/주소를 먼저 만드세요 (seedDummyUsers)." }

        // id에 결번이 생겨도 한두 번 못 찾는 걸로 생성이 멈추지 않게 몇 번 다시 뽑는다.
        repeat(MAX_ATTEMPTS) {
            addressRepository.findById(Random.nextLong(1, max + 1)).orElse(null)?.let { return it }
        }
        error("무작위로 고른 주소 id가 ${MAX_ATTEMPTS}번 연속 존재하지 않았습니다. 주소 id에 결번이 많은지 확인하세요.")
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
