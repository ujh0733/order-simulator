package dev.junghun.ordersimulator.order

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

/** 주문 상태 전이 이력. fromStatus가 null이면 최초 접수(RECEIVED) 이벤트다. */
@Entity
@Table(name = "order_status_events")
class OrderStatusEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    val fromStatus: OrderStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    val toStatus: OrderStatus,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now(),
)
