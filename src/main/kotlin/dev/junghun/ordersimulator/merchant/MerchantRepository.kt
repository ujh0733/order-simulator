package dev.junghun.ordersimulator.merchant

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

private const val ACTIVE_STATUS_CODE = "01"

interface MerchantRepository : JpaRepository<Merchant, Long> {

    // ORDER BY RAND()는 가게가 53만 건이 되자 전체 정렬 때문에 실측 초당 1건 미만으로 느려졌다.
    // id 범위를 무작위로 잡고 (business_status_code, id) 인덱스로 그 이후 첫 영업 중 가게를
    // 찾는 방식으로 바꿨다. 무작위 지점 이후에 영업 중인 가게가 하나도 없는 극히 드문 경우를
    // 대비해 findFirstActive()로 폴백한다.
    @Query(
        value = """
            SELECT * FROM merchants
            WHERE business_status_code = '$ACTIVE_STATUS_CODE'
              AND id >= (SELECT FLOOR(RAND() * (SELECT MAX(id) FROM merchants)) + 1)
            ORDER BY id LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findRandomActive(): Merchant?

    @Query(
        value = "SELECT * FROM merchants WHERE business_status_code = '$ACTIVE_STATUS_CODE' ORDER BY id LIMIT 1",
        nativeQuery = true,
    )
    fun findFirstActive(): Merchant?
}
