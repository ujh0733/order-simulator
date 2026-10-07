package dev.junghun.ordersimulator.order

import dev.junghun.ordersimulator.address.RandomAddressPicker
import dev.junghun.ordersimulator.merchant.ActiveMerchantPool
import dev.junghun.ordersimulator.merchant.MerchantDeliveryEstimate
import dev.junghun.ordersimulator.merchant.MerchantDeliveryEstimateRepository
import dev.junghun.ordersimulator.merchant.MerchantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.random.Random

// "60분 이내"라는 상한 조건은 지키되, 실제 값은 조리시간(1~15분)과 비슷한 범위로 짧게 잡아서
// 완료(COMPLETED)까지 실제로 눈으로 확인할 수 있게 한다. 예전 15~60분 범위는 완료가 나오기까지
// 너무 오래 걸려서(최소 15분) 체감상 "완료 건이 하나도 없다"는 문제가 있었다.
private const val MAX_DELIVERY_MINUTES = 15
private const val MIN_DELIVERY_MINUTES = 1

@Service
class OrderGeneratorService(
    private val orderRepository: OrderRepository,
    private val randomAddressPicker: RandomAddressPicker,
    private val merchantRepository: MerchantRepository,
    private val activeMerchantPool: ActiveMerchantPool,
    private val deliveryEstimateRepository: MerchantDeliveryEstimateRepository,
    private val orderStatusEventRepository: OrderStatusEventRepository,
) {

    // 스케줄러(HTTP 요청 밖)에서도 호출되므로, address.user 같은 지연 로딩 연관관계를 안전히
    // 쓰려면 이 메서드 자체가 트랜잭션 경계를 가져야 한다(open-in-view는 요청 스레드에서만 동작).
    @Transactional
    fun generateOrder(): Order {
        val address = randomAddressPicker.pick()
        // 주문에는 가게 id 참조만 필요해서 SELECT 없는 프록시로 충분하다.
        val merchantId = activeMerchantPool.pickRandomId()
        val merchant = merchantRepository.getReferenceById(merchantId)

        val estimate = deliveryEstimateRepository.findByMerchantIdAndRegionId(merchantId, address.region.id!!)
            ?: deliveryEstimateRepository.save(
                MerchantDeliveryEstimate(
                    merchant = merchant,
                    region = address.region,
                    estimatedDeliveryMinutes = Random.nextInt(MIN_DELIVERY_MINUTES, MAX_DELIVERY_MINUTES + 1),
                )
            )

        val order = Order(
            user = address.user,
            address = address,
            merchant = merchant,
            estimatedDeliveryMinutes = estimate.estimatedDeliveryMinutes,
            orderedAt = LocalDateTime.now(),
        )
        val saved = orderRepository.save(order)

        // 최초 접수 이벤트. fromStatus가 없는 게 "새로 생성됨"을 뜻한다.
        orderStatusEventRepository.save(
            OrderStatusEvent(order = saved, fromStatus = null, toStatus = OrderStatus.RECEIVED)
        )
        return saved
    }
}
