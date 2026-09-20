package dev.junghun.ordersimulator.simulation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

data class LoadGenerationRequest(val count: Int, val durationMinutes: Int)

data class LoadGenerationStatusResponse(
    val total: Int,
    val completed: Int,
    val durationMinutes: Int,
    val elapsedSeconds: Long,
    val running: Boolean,
    val failedMessage: String?,
)

data class ErrorResponse(val message: String)

@RestController
@RequestMapping("/api/simulation/load")
class LoadGenerationController(
    private val loadGenerationService: LoadGenerationService,
    private val state: LoadGenerationState,
) {

    @PostMapping
    fun start(@RequestBody request: LoadGenerationRequest): LoadGenerationStatusResponse {
        loadGenerationService.start(request.count, request.durationMinutes)
        return status()
    }

    @GetMapping
    fun status(): LoadGenerationStatusResponse {
        val elapsedSeconds = state.startedAt?.let { Duration.between(it, Instant.now()).seconds } ?: 0
        return LoadGenerationStatusResponse(
            total = state.total,
            completed = state.completed.get(),
            durationMinutes = state.durationMinutes,
            elapsedSeconds = elapsedSeconds,
            running = state.running,
            failedMessage = state.failedMessage,
        )
    }

    @ExceptionHandler(IllegalStateException::class, IllegalArgumentException::class)
    fun handleInvalidRequest(e: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(e.message ?: "요청을 처리할 수 없습니다."))
}
