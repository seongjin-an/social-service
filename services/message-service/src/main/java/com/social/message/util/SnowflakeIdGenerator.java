package com.social.message.util;

public class SnowflakeIdGenerator {

    // 2024-01-01 00:00:00 UTC
    private static final long EPOCH = 1704067200000L;

    // worker 5bit (0~31)
    private static final long WORKER_ID_BITS = 5L;

    // sequence 11bit (0~2047)
    private static final long SEQUENCE_BITS = 11L;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT =
        WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this(0);
    }

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                "workerId must be between 0 and " + MAX_WORKER_ID
            );
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {

        long ts = System.currentTimeMillis();

        if (ts < lastTimestamp) {
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate id."
            );
        }

        if (ts == lastTimestamp) {

            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                ts = waitNextMillis(lastTimestamp);
            }

        } else {
            sequence = 0;
        }

        lastTimestamp = ts;

        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {

        long ts;

        do {
            ts = System.currentTimeMillis();
            Thread.onSpinWait();
        } while (ts <= lastTimestamp);

        return ts;
    }
}
