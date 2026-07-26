package com.social.message.util;

import org.springframework.beans.factory.annotation.Value;

public class SnowflakeIdGeneratorG {

    // 2024-01-01 00:00:00 UTC
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 4L;      // 최대 16대 서버
    private static final long SEQUENCE_BITS = 8L;       // 밀리초당 256개 ID (초당 256,000개)

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1; // 15
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;   // 255

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    // 인프라 환경(k8s pod id, env, 또는 application.yml)에서 서버별 고유 ID를 주입받도록 설정
    public SnowflakeIdGeneratorG(@Value("${server.worker-id:0}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                String.format("Worker ID는 0과 %d 사이여야 합니다.", MAX_WORKER_ID));
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long ts = System.currentTimeMillis();

        // [중요] NTP 동기화 등으로 서버 시간이 과거로 흘렀을 경우 (Clock Skew 방어)
        if (ts < lastTimestamp) {
            // 시스템 상황에 따라 예외를 던지거나, 잠시 대기하게 할 수 있습니다. 여기선 예외 처리.
            throw new RuntimeException(
                String.format("클럭이 역전되었습니다. %d 밀리초 동안 ID 생성이 거부됩니다.", lastTimestamp - ts));
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 해당 밀리초의 시퀀스가 꽉 차면 다음 밀리초로 넘어갈 때까지 대기
                while ((ts = System.currentTimeMillis()) <= lastTimestamp) {
                }
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = ts;

        // 53비트 ID 조립하여 반환
        return ((ts - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }
}
