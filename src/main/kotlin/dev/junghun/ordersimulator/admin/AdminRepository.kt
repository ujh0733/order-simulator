package dev.junghun.ordersimulator.admin

import org.springframework.data.jpa.repository.JpaRepository

interface AdminRepository : JpaRepository<Admin, Long> {
    fun findByUsername(username: String): Admin?
}
