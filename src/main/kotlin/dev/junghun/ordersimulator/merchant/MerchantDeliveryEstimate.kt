package dev.junghun.ordersimulator.merchant

import dev.junghun.ordersimulator.region.Region
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 가게(merchant) 하나가 특정 배달지역(region)까지 걸리는 예상 배달 시간(분, 최대 60). */
@Entity
@Table(name = "merchant_delivery_estimates")
class MerchantDeliveryEstimate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    val merchant: Merchant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    val region: Region,

    @Column(name = "estimated_delivery_minutes", nullable = false)
    val estimatedDeliveryMinutes: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
