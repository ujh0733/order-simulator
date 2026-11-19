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

interface MerchantOrderCount {
    fun getMerchantId(): Long
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

    /**
     * 조리 시작 대상 - 대기(RECEIVED) 주문을 오래된 순으로 가게와 함께 한 번에 읽는다.
     * 가게마다 따로 조회하지 않도록 가게를 JOIN FETCH 한다. id는 접수 순서대로 증가하므로 id 순이 곧 접수 순이다.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.merchant WHERE o.status = :status ORDER BY o.id")
    fun findOldestWithMerchantByStatus(@Param("status") status: OrderStatus, pageable: Pageable): List<Order>

    /** 가게별 상태 건수를 한 번에 센다 - 가게마다 COUNT를 따로 날리지 않기 위해서다. */
    @Query(
        """
        SELECT o.merchant.id AS merchantId, COUNT(o) AS orderCount
        FROM Order o
        WHERE o.status = :status AND o.merchant.id IN :merchantIds
        GROUP BY o.merchant.id
        """
    )
    fun countByMerchantIdsAndStatus(
        @Param("merchantIds") merchantIds: Collection<Long>,
        @Param("status") status: OrderStatus,
    ): List<MerchantOrderCount>

    /** 조리시간이 다 지나(cookingReadyAt 경과) 배차를 시도해야 하는 주문 - 먼저 준비된 순으로 limit건. */
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.cookingReadyAt <= :now ORDER BY o.cookingReadyAt")
    fun findReadyToDispatch(
        @Param("status") status: OrderStatus,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<Order>

    /**
     * 배달 예상시간이 지난 배차 중(DISPATCHED) 주문만 limit건. 예전에는 배차 중 주문 전체를 읽어서 메모리에서
     * 걸렀는데, 라이더가 37만 명으로 늘면서 배차 중 주문이 수천 건이 되자 매 tick 그 전체를 읽게 되었다.
     */
    @Query(
        value = """
        SELECT * FROM orders
        WHERE status = 'DISPATCHED'
          AND dispatched_at <= DATE_SUB(:now, INTERVAL estimated_delivery_minutes MINUTE)
        ORDER BY dispatched_at
        LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findDelivered(@Param("now") now: LocalDateTime, @Param("limit") limit: Int): List<Order>

    /** 대시보드의 배송 중 목록에 사용. */
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
