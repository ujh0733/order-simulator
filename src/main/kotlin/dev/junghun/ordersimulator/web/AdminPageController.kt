package dev.junghun.ordersimulator.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class AdminPageController {

    @GetMapping("/")
    fun index(): String = "redirect:/admin"

    @GetMapping("/admin")
    fun dashboard(): String = "admin/dashboard"

    @GetMapping("/admin/orders")
    fun orders(): String = "admin/orders"
}
