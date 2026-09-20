package dev.junghun.ordersimulator.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface RegionOrderCount {
    fun getRegionId(): Long
    fun getOrderCount(): Long
}

interface RegionPairOrderCount {
    fun getCustomerRegionId(): Long
    fun getMerchantRegionId(): Long
    fun getOrderCount(): Long
}

interface OrderRepository : JpaRepository<Order, Long> {

    /** 주문자(배달주소) 기준 지역별 건수 - 대시보드 지도 색칠에 사용. */
    @Query(
        """
        SELECT o.address.region.id AS regionId, COUNT(o) AS orderCount
        FROM Order o
        WHERE o.orderedAt >= :from AND o.orderedAt < :to
        GROUP BY o.address.region.id
        """
    )
    fun countByCustomerRegionBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<RegionOrderCount>

    /** 주문자 지역 x 가게 지역 조합별 건수 - 지도 hover 시 "어느 가게 지역에서 왔는지" 범례용. */
    @Query(
        """
        SELECT o.address.region.id AS customerRegionId, o.merchant.region.id AS merchantRegionId, COUNT(o) AS orderCount
        FROM Order o
        WHERE o.orderedAt >= :from AND o.orderedAt < :to
        GROUP BY o.address.region.id, o.merchant.region.id
        """
    )
    fun countByCustomerAndMerchantRegionBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<RegionPairOrderCount>

    /** 가게의 동시 조리 중 건수 - 용량 제한 체크용. */
    fun countByMerchantIdAndStatus(merchantId: Long, status: OrderStatus): Long

    /** 가게에서 대기 중(RECEIVED)인 주문 중 가장 오래된 것 - 조리 시작 대상. */
    fun findFirstByMerchantIdAndStatusOrderByOrderedAtAsc(merchantId: Long, status: OrderStatus): Order?

    /** 지금 대기 중인 주문이 있는 가게 목록 - 스케줄러가 이 가게들만 용량 체크하면 된다. */
    @Query("SELECT DISTINCT o.merchant.id FROM Order o WHERE o.status = :status")
    fun findDistinctMerchantIdsByStatus(@Param("status") status: OrderStatus): List<Long>

    /** 가게별 조리시간이 다 지나(cookingReadyAt 경과) 배차를 시도해야 하는 주문들. */
    fun findByStatusAndCookingReadyAtLessThanEqual(status: OrderStatus, now: LocalDateTime): List<Order>

    /** 라이더 풀(50명) 크기로 자연히 제한되는 소규모 집합이라 전체 조회 후 메모리에서 걸러도 된다. */
    fun findByStatus(status: OrderStatus): List<Order>

    /** 파이프라인 현황판(접수/조리/배차/완료 건수)에 사용. */
    fun countByStatus(status: OrderStatus): Long

    /**
     * 주문 내역 페이지 - 상태/지역/기간/키워드로 걸러서 최신순 페이징 조회. 조건은 전부 선택사항.
     * 키워드는 주문번호, 주문자 이름/전화번호, 가게명을 한 번에 검색한다.
     */
    @Query(
        """
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
        AND (:regionId IS NULL OR o.address.region.id = :regionId)
        AND (:from IS NULL OR o.orderedAt >= :from)
        AND (:to IS NULL OR o.orderedAt < :to)
        AND (
            :keyword IS NULL
            OR str(o.id) LIKE CONCAT('%', :keyword, '%')
            OR o.user.name LIKE CONCAT('%', :keyword, '%')
            OR o.user.phone LIKE CONCAT('%', :keyword, '%')
            OR o.merchant.name LIKE CONCAT('%', :keyword, '%')
        )
        ORDER BY o.orderedAt DESC
        """
    )
    fun search(
        @Param("status") status: OrderStatus?,
        @Param("regionId") regionId: Long?,
        @Param("from") from: LocalDateTime?,
        @Param("to") to: LocalDateTime?,
        @Param("keyword") keyword: String?,
        pageable: Pageable,
    ): Page<Order>
}
