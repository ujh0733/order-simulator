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
}
