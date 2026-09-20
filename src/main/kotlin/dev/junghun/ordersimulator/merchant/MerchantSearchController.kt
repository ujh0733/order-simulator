package dev.junghun.ordersimulator.merchant

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/merchants")
class MerchantSearchController(
    private val merchantSearchService: MerchantSearchService,
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) sido: String?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) businessStatus: String?,
        @RequestParam(required = false) cookingMinutes: Int?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): MerchantPage = merchantSearchService.search(
        MerchantSearchCondition(sido, region, businessStatus, cookingMinutes, keyword),
        page,
        size,
    )

    @GetMapping("/facets")
    fun facets(): MerchantFacets = merchantSearchService.facets()
}
