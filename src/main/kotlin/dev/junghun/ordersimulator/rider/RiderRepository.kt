package dev.junghun.ordersimulator.rider

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

private const val AVAILABLE_STATUS = "AVAILABLE"

interface RiderRepository : JpaRepository<Rider, Long> {

    @Query("SELECT MAX(r.id) FROM Rider r")
    fun findMaxId(): Long?

    // 무작위로 여러 번 골라도 가용 라이더를 못 찾을 때만 쓰는 폴백이다(RandomRiderPicker).
    @Query(
        value = "SELECT * FROM riders WHERE status = '$AVAILABLE_STATUS' ORDER BY id LIMIT 1",
        nativeQuery = true,
    )
    fun findFirstAvailable(): Rider?

    fun countByStatus(status: RiderStatus): Long

    // 배차 tick은 가용 라이더 "총 수"가 아니라 "배차할 주문 수만큼 있는지"만 알면 된다.
    // InnoDB는 행 수를 따로 들고 있지 않아 COUNT(*)가 조건에 맞는 인덱스 항목(약 37만 개)을 매번 다 세므로,
    // 서브쿼리에 LIMIT을 걸어 limit개까지만 읽고 멈추게 한다.
    @Query(
        value = "SELECT COUNT(*) FROM (SELECT 1 FROM riders WHERE status = '$AVAILABLE_STATUS' LIMIT :limit) t",
        nativeQuery = true,
    )
    fun countAvailableUpTo(@Param("limit") limit: Int): Long

    // 라이더를 고르는 시점과 실제로 잡는(claim) 시점 사이에, 다른 트랜잭션이 먼저 같은 라이더를 채갈 수 있다
    // (특히 tick()이 오래 걸려 두 번이 겹쳐 돌 때). "AVAILABLE일 때만 BUSY로 바꾼다"를 하나의 원자적
    // UPDATE로 만들어서, 두 트랜잭션이 동시에 성공하는 걸 막는다. 갱신된 행이 0이면 이미 다른 곳에서
    // 잡아갔거나 존재하지 않는 id라서, 호출한 쪽에서 다른 라이더를 고르거나 이번 시도를 포기해야 한다.
    @Modifying
    @Transactional
    @Query(
        """
        UPDATE Rider r
        SET r.status = dev.junghun.ordersimulator.rider.RiderStatus.BUSY
        WHERE r.id = :id AND r.status = dev.junghun.ordersimulator.rider.RiderStatus.AVAILABLE
        """
    )
    fun claimIfAvailable(@Param("id") id: Long): Int

    // 배달을 마친 라이더들을 한 번에 가용 상태로 돌린다. 엔티티를 하나씩 읽어 상태를 바꾸면
    // 라이더마다 SELECT와 전체 컬럼 UPDATE가 따로 나간다.
    @Modifying
    @Query(
        """
        UPDATE Rider r
        SET r.status = dev.junghun.ordersimulator.rider.RiderStatus.AVAILABLE
        WHERE r.id IN :ids
        """
    )
    fun releaseAll(@Param("ids") ids: Collection<Long>): Int
}
