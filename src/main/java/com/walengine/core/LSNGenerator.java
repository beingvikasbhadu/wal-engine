package com.walengine.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LSNGenerator produces monotonically increasing Log Sequence Numbers.
 *
 * Format: [timestamp_ms - epoch (42 bits)] | [sequence (22 bits)]
 *   - ~139 years before overflow
 *   - 4,194,303 unique LSNs per millisecond
 *   - Time-sortable (used for replication lag calculation)
 */
public class LSNGenerator {

    private static final long SEQUENCE_BITS  = 22L;
    private static final long SEQUENCE_MASK  = (1L << SEQUENCE_BITS) - 1;
    private static final long EPOCH          = 1700000000000L; // Nov 2023

    private volatile long lastTimestamp = -1L;
    private volatile long sequence      = 0L;
    private final AtomicLong lastLsn    = new AtomicLong(0L);

    public synchronized long next() {
        long ts = System.currentTimeMillis();

        if (ts < lastTimestamp) {
            throw new IllegalStateException(
                "Clock moved backwards " + (lastTimestamp - ts) + "ms. Cannot generate LSN.");
        }

        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) ts = waitNextMillis(lastTimestamp);
        } else {
            sequence = 0;
        }

        lastTimestamp = ts;
        long lsn = ((ts - EPOCH) << SEQUENCE_BITS) | sequence;
        lastLsn.set(lsn);
        return lsn;
    }

    /** Called after crash recovery to prevent LSN reuse. */
    public void restore(long recoveredLsn) {
        lastLsn.set(recoveredLsn);
        lastTimestamp = (recoveredLsn >> SEQUENCE_BITS) + EPOCH;
        sequence = recoveredLsn & SEQUENCE_MASK;
    }

    public long getLastLsn() { return lastLsn.get(); }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) { Thread.onSpinWait(); ts = System.currentTimeMillis(); }
        return ts;
    }
}
