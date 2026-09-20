package dev.junghun.ordersimulator.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 내부 PK(id)와는 별개로 외부에 노출 가능한 개인 식별자.
    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    val userId: String,

    @Column(nullable = false, length = 50)
    val name: String,

    @Column(nullable = false, unique = true, length = 20)
    val phone: String,

    @Column(length = 100)
    val email: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
