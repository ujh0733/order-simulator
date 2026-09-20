package dev.junghun.ordersimulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OrderSimulatorApplication

fun main(args: Array<String>) {
	runApplication<OrderSimulatorApplication>(*args)
}
