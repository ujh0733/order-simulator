package dev.junghun.ordersimulator.address

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AddressRepository : JpaRepository<Address, Long> {
    fun findByUserId(userId: Long): List<Address>

    // 무작위 주소는 DB 쿼리의 RAND()가 아니라 앱에서 1~MAX(id) 난수를 한 번 뽑아 PK로 조회한다(RandomAddressPicker).
    @Query("SELECT MAX(a.id) FROM Address a")
    fun findMaxId(): Long?
}
