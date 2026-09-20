package dev.junghun.ordersimulator.simulation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface LoadGenerationJobRepository : JpaRepository<LoadGenerationJob, Long> {
    /** 서버 시작 시 "재시작 전에 돌고 있던 작업이 있었는지" 확인용. */
    fun findFirstByRunningTrueOrderByIdDesc(): LoadGenerationJob?

    // 스케줄러(같은 클래스 내부 self-invocation)에서 안전하게 커밋되도록, entity를 조회해서
    // 값을 바꾸는 대신 리포지토리 프록시를 통한 직접 UPDATE로 처리한다. @Transactional을
    // 여기 붙여야 한다 - 커스텀 @Query 메서드는 SimpleJpaRepository의 기본 트랜잭션을
    // 물려받지 않는다.
    @Modifying
    @Transactional
    @Query(
        """
        UPDATE LoadGenerationJob j
        SET j.completed = :completed, j.running = :running, j.failedMessage = :failedMessage
        WHERE j.id = :id
        """
    )
    fun updateProgress(
        @Param("id") id: Long,
        @Param("completed") completed: Int,
        @Param("running") running: Boolean,
        @Param("failedMessage") failedMessage: String?,
    )
}
