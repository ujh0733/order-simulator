package dev.junghun.ordersimulator.region

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class RegionLevel {
    /** 시/도 (예: 서울특별시). 다른 지역으로 확장할 때 이 레벨에 새 로우를 추가한다. */
    SIDO,

    /** 자치구/시/군 (예: 강남구). 주문·배달 집계의 기본 단위. */
    GU,
}

@Entity
@Table(name = "regions")
class Region(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: Region? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val level: RegionLevel,

    @Column(nullable = false, length = 50)
    val name: String,

    // 공공데이터포털 API의 개방자치단체코드(OPN_ATMY_GRP_CD) 등 외부 코드. 없으면 null.
    @Column(name = "external_code", length = 20)
    val externalCode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
