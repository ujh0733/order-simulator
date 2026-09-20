package dev.junghun.ordersimulator.order

import dev.junghun.ordersimulator.address.Address
import dev.junghun.ordersimulator.merchant.Merchant
import dev.junghun.ordersimulator.rider.Rider
import dev.junghun.ordersimulator.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/** README 시나리오의 접수 → 조리 → 배차 → 완료 흐름. [OrderLifecycleScheduler]가 이 상태를 실제로 전이시킨다. */
enum class OrderStatus {
    RECEIVED,
    COOKING,
    DISPATCHED,
    COMPLETED,
}

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    // 배달지역 집계(대시보드 지도)는 이 주소의 region을 기준으로 한다 - "주문자 기준" 통계.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    val address: Address,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    val merchant: Merchant,

    // 배차되기 전까지는 null. 조리(COOKING) 중에는 아직 라이더가 없다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    var rider: Rider? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.RECEIVED,

    @Column(name = "estimated_delivery_minutes", nullable = false)
    val estimatedDeliveryMinutes: Int,

    @Column(name = "cooking_started_at")
    var cookingStartedAt: LocalDateTime? = null,

    // cookingStartedAt + 가게별 cookingMinutes. 배차 대상 조회를 단순 비교로 끝내기 위해 미리 계산해둔다.
    @Column(name = "cooking_ready_at")
    var cookingReadyAt: LocalDateTime? = null,

    @Column(name = "dispatched_at")
    var dispatchedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "ordered_at", nullable = false)
    val orderedAt: LocalDateTime,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
