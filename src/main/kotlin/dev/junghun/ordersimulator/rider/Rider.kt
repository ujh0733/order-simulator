package dev.junghun.ordersimulator.rider

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class RiderStatus {
    AVAILABLE,
    BUSY,
}

@Entity
@Table(name = "riders")
class Rider(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 50)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RiderStatus = RiderStatus.AVAILABLE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
