package com.gba.merchantbao.common.id;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 41-bit milliseconds since 2026-01-01 UTC, 10-bit worker, 12-bit sequence. */
public final class SnowflakeIdGenerator implements IdGenerator {
    public static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long MAX_ELAPSED = (1L << 41) - 1;
    private final int workerId;
    private final Clock clock;
    private long lastMillis = -1;
    private int sequence;

    public SnowflakeIdGenerator(int workerId, Clock clock) {
        if (workerId < 0 || workerId > 1023) {
            throw new IllegalArgumentException("merchant.id.worker-id must be between 0 and 1023");
        }
        this.workerId = workerId;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized long nextId() {
        long now = clock.millis();
        long elapsed = now - EPOCH_MILLIS;
        if (elapsed < 0 || elapsed > MAX_ELAPSED) {
            throw new IllegalStateException("Clock is outside the Snowflake epoch range");
        }
        if (now < lastMillis) {
            throw new IllegalStateException("Clock moved backwards; refusing to generate an ID");
        }
        if (now == lastMillis) {
            if (sequence == 4095) {
                // Fail rather than spin forever on a stalled clock or manufacture future timestamps.
                throw new IllegalStateException("Snowflake sequence exhausted; retry after the clock advances");
            }
            sequence++;
        } else {
            sequence = 0;
        }
        lastMillis = now;
        return (elapsed << 22) | ((long) workerId << 12) | sequence;
    }
}
