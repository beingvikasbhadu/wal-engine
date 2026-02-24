package com.walengine.model;

public class WALCorruptionException extends RuntimeException {
    private final long corruptedLsn;

    public WALCorruptionException(String message) {
        super(message);
        this.corruptedLsn = -1;
    }

    public WALCorruptionException(String message, long corruptedLsn) {
        super(message);
        this.corruptedLsn = corruptedLsn;
    }

    public WALCorruptionException(String message, Throwable cause) {
        super(message, cause);
        this.corruptedLsn = -1;
    }

    public long getCorruptedLsn() { return corruptedLsn; }
}
