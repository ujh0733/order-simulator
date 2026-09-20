package dev.junghun.ordersimulator.rider

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RiderRepository : JpaRepository<Rider, Long> {

    // 라이더 풀(50명 고정)에서 배차 가능한 라이더를 무작위로 하나 선택한다.
    @Query(value = "SELECT * FROM riders WHERE status = 'AVAILABLE' ORDER BY RAND() LIMIT 1", nativeQuery = true)
    fun findRandomAvailable(): Rider?

    fun countByStatus(status: RiderStatus): Long
}
