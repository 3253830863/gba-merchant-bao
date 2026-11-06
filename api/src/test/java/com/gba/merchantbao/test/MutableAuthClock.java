package com.gba.merchantbao.test;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

final class MutableAuthClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    void advance(Duration duration) { now.updateAndGet(t -> t.plus(duration)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now.get(); }
}
