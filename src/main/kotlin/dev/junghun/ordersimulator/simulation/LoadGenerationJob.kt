package dev.junghun.ordersimulator.simulation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * "N건을 M분 동안 생성" 작업의 영속 상태. 서버가 재시작돼도 이 로우를 보고
 * 남은 건수/남은 시간을 다시 계산해서 이어갈 수 있다.
 */
@Entity
@Table(name = "load_generation_jobs")
class LoadGenerationJob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val total: Int,

    @Column(nullable = false)
    var completed: Int = 0,

    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,

    @Column(nullable = false)
    var running: Boolean = true,

    @Column(name = "failed_message", length = 500)
    var failedMessage: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
