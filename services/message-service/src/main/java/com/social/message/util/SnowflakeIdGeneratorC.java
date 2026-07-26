package com.social.message.util;

public class SnowflakeIdGeneratorC {

    // 2024-01-01 00:00:00 UTC
    private static final long EPOCH = 1704067200000L;
    private static final long SEQUENCE_BITS = 11L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1; // 2047
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    // 41bit timestamp + 11bit sequence = 52bit → JS Number safe integer 범위 내
    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                while ((ts = System.currentTimeMillis()) <= lastTimestamp) {
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT) | sequence;
    }
}
