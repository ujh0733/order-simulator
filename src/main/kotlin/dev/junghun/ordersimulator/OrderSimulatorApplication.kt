package dev.junghun.ordersimulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OrderSimulatorApplication

fun main(args: Array<String>) {
	runApplication<OrderSimulatorApplication>(*args)
}
