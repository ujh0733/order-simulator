package dev.junghun.ordersimulator.order

import org.springframework.data.jpa.repository.JpaRepository

interface OrderStatusEventRepository : JpaRepository<OrderStatusEvent, Long> {

    /** 파이프라인 패널의 "최근 이벤트" 피드에 사용. */
    fun findTop20ByOrderByOccurredAtDesc(): List<OrderStatusEvent>
}
