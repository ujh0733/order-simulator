package dev.junghun.ordersimulator.merchant

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantDeliveryEstimateRepository : JpaRepository<MerchantDeliveryEstimate, Long> {
    fun findByMerchantIdAndRegionId(merchantId: Long, regionId: Long): MerchantDeliveryEstimate?
}
