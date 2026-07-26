package com.social.message.outbox;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PROCESSED,
    FAILED
}
