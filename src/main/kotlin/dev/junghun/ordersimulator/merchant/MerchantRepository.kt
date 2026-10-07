package dev.junghun.ordersimulator.merchant

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

private const val ACTIVE_STATUS_CODE = "01"

interface MerchantRepository : JpaRepository<Merchant, Long> {

    // 영업 중인 가게를 DB에서 무작위로 뽑으면(WHERE 안의 RAND()) 난수가 행마다 다시 계산돼서 낮은 id로만
    // 쏠린다. 그래서 id 전체를 한 번만 읽어 앱에서 고른다(ActiveMerchantPool). (business_status_code, id)
    // 인덱스만으로 읽는 커버링 스캔이라 12만 건도 수십 ms면 끝난다.
    @Query("SELECT m.id FROM Merchant m WHERE m.businessStatusCode = '$ACTIVE_STATUS_CODE'")
    fun findActiveIds(): List<Long>
}
