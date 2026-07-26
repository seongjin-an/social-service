package com.social.message.outbox;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxEventCleaner {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${chatting.outbox.retention-days:1}")
    private int retentionDays;

    // Debezium이 binlog를 읽은 후 status를 갱신하지 않아 레코드가 무한 누적되는 문제 방지
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOldEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = outboxEventRepository.deleteOldEvents(cutoff);
        log.info("[OutboxCleaner] Deleted {} outbox events older than {} days", deleted, retentionDays);
    }
}
