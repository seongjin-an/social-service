package com.social.message.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // 멀티 인스턴스 환경에서 중복 처리 방지: 다른 트랜잭션이 잠근 row는 건너뜀
    @Query(value = "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> findPendingForUpdate();

    // Debezium이 발행 후 status를 업데이트하지 않으므로 오래된 PENDING 레코드 주기적 정리
    @Modifying
    @Query(value = "DELETE FROM outbox WHERE created_at < :cutoff", nativeQuery = true)
    int deleteOldEvents(@Param("cutoff") Instant cutoff);
}
