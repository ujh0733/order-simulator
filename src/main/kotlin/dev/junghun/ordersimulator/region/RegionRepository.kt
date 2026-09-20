package dev.junghun.ordersimulator.region

import org.springframework.data.jpa.repository.JpaRepository

interface RegionRepository : JpaRepository<Region, Long> {
    fun findByLevel(level: RegionLevel): List<Region>
    fun findByLevelAndParentId(level: RegionLevel, parentId: Long): List<Region>
    fun findByLevelAndName(level: RegionLevel, name: String): Region?
}
