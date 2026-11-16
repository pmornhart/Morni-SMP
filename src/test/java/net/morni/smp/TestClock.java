package net.morni.smp;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Uhr für Tests: steht still, bis man sie weiterdreht. */
public final class TestClock implements LongSupplier {

    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);

    @Override
    public long getAsLong() {
        return now.get();
    }

    public void advance(long ms) {
        now.addAndGet(ms);
    }
}
