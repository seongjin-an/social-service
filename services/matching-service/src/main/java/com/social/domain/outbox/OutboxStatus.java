package com.social.domain.outbox;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PROCESSED,
    FAILED
}
