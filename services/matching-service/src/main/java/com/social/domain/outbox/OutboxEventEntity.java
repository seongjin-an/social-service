package com.social.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "outbox")
@Entity
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID eventId;

    private UUID sagaId;
    private String sagaType;

    private String aggregateType;
    private String aggregateId;

    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "destination_topic", nullable = false)
    private String destinationTopic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;
    private int retryCount;
    private String lastError;

    private String traceId;
    private String spanId;

    private Instant createdAt;
    private Instant processedAt;

    @Version
    private Long version;

    public static OutboxEventEntity create(
        String aggregateType, String aggregateId,
        String eventType, String payload,
        String destinationTopic, String partitionKey
    ) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.eventId = UUID.randomUUID();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.destinationTopic = destinationTopic;
        e.partitionKey = partitionKey;
        e.status = OutboxStatus.PENDING;
        e.retryCount = 0;
        e.createdAt = Instant.now();
        e.traceId = MDC.get("trace_id");
        e.spanId = MDC.get("span_id");
        return e;
    }

    public void markInProgress() {
        this.status = OutboxStatus.IN_PROGRESS;
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.retryCount = this.retryCount + 1;
        this.lastError = error;
    }
}
