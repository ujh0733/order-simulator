package dev.junghun.ordersimulator.address

import dev.junghun.ordersimulator.region.Region
import dev.junghun.ordersimulator.user.User
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

@Entity
@Table(name = "addresses")
class Address(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    // 배달지역 통계/집계의 기준이 되는 지역(GU).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    val region: Region,

    @Column(name = "road_address", nullable = false, length = 200)
    val roadAddress: String,

    @Column(name = "lot_address", length = 200)
    val lotAddress: String? = null,

    @Column(name = "detail_address", length = 100)
    val detailAddress: String? = null,

    @Column(name = "zip_code", length = 10)
    val zipCode: String? = null,

    @Column(name = "is_default", nullable = false)
    val isDefault: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
