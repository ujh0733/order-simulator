package dev.junghun.ordersimulator.address

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AddressRepository : JpaRepository<Address, Long> {
    fun findByUserId(userId: Long): List<Address>

    // ORDER BY RAND()는 addresses가 100만 건에 육박하자 전체 정렬 때문에 초당 1건도 못 뽑을 만큼
    // 느려졌다(실측). id 범위를 무작위로 잡고 그 이후 첫 행을 PK 인덱스로 찾는 방식으로 바꿔
    // 정렬 없이 인덱스 스캔만으로 끝내도록 했다.
    @Query(
        value = """
            SELECT * FROM addresses
            WHERE id >= (SELECT FLOOR(RAND() * (SELECT MAX(id) FROM addresses)) + 1)
            ORDER BY id LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findRandom(): Address?
}
