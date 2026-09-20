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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "merchants")
class Merchant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    val region: Region,

    // 공공데이터포털 MNG_NO. 재수집해도 같은 가게면 값이 같아 upsert 키로 쓴다.
    @Column(name = "management_no", nullable = false, unique = true, length = 50)
    val managementNo: String,

    @Column(nullable = false, length = 200)
    val name: String,

    @Column(name = "business_status_code", length = 10)
    val businessStatusCode: String? = null,

    @Column(name = "business_status_name", length = 50)
    val businessStatusName: String? = null,

    @Column(name = "detail_status_name", length = 50)
    val detailStatusName: String? = null,

    @Column(name = "license_date")
    val licenseDate: LocalDate? = null,

    @Column(name = "close_date")
    val closeDate: LocalDate? = null,

    @Column(name = "road_address", length = 300)
    val roadAddress: String? = null,

    @Column(name = "lot_address", length = 300)
    val lotAddress: String? = null,

    @Column(length = 30)
    val phone: String? = null,

    @Column(name = "coord_x", precision = 15, scale = 6)
    val coordX: BigDecimal? = null,

    @Column(name = "coord_y", precision = 15, scale = 6)
    val coordY: BigDecimal? = null,

    // 이 가게가 동시에 조리할 수 있는 최대 건수. 초과하는 주문은 RECEIVED 상태로 대기한다.
    @Column(name = "max_concurrent_cooking", nullable = false)
    val maxConcurrentCooking: Int = 5,

    // 가게별 조리 소요시간(분). 1~15분 사이 무작위로 시드되어, 빠른 가게는 배차까지 금방 넘어간다.
    @Column(name = "cooking_minutes", nullable = false)
    val cookingMinutes: Int = 10,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
