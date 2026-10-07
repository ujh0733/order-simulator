package dev.junghun.ordersimulator.merchant

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 영업 중인 가게 id를 메모리에 들고 있다가, 주문마다 난수 1번으로 배열 위치 하나를 골라 균등하게 선택한다.
 * 12만 개 id는 LongArray로 약 1MB다.
 */
@Component
class ActiveMerchantPool(
    private val merchantRepository: MerchantRepository,
) {

    @Volatile
    private var ids: LongArray = LongArray(0)

    fun reload() {
        ids = merchantRepository.findActiveIds().toLongArray()
    }

    fun pickRandomId(): Long {
        val snapshot = ids // reload와 겹쳐도 한 번 잡은 배열로 끝까지 고른다
        check(snapshot.isNotEmpty()) { "영업 중인 가게가 없습니다. 가게 데이터를 먼저 수집하세요 (collectSeoulRestaurants)." }
        return snapshot[Random.nextInt(snapshot.size)]
    }
}
