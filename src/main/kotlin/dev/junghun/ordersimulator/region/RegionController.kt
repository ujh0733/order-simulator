package dev.junghun.ordersimulator.region

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class SidoResponse(val id: Long, val name: String)
data class GuResponse(val id: Long, val name: String, val externalCode: String?)

@RestController
@RequestMapping("/api/regions")
class RegionController(
    private val regionRepository: RegionRepository,
) {

    /** 메뉴에 표시할 시/도 목록. 지금은 서울특별시만 있고, 다른 지역은 SIDO 로우를 추가하면 자동으로 노출된다. */
    @GetMapping("/sido")
    fun listSido(): List<SidoResponse> =
        regionRepository.findByLevel(RegionLevel.SIDO).map { SidoResponse(it.id!!, it.name) }

    /** 특정 시/도의 구/시/군 목록. 지도가 없는 지역이라도 리스트는 이걸로 보여줄 수 있다. */
    @GetMapping("/gu")
    fun listGu(@RequestParam sido: String): List<GuResponse> {
        val sidoRegion = regionRepository.findByLevelAndName(RegionLevel.SIDO, sido)
            ?: return emptyList()
        return regionRepository.findByLevelAndParentId(RegionLevel.GU, sidoRegion.id!!)
            .map { GuResponse(it.id!!, it.name, it.externalCode) }
    }
}
