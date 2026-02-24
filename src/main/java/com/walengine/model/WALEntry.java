package com.walengine.model;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * WALEntry is the atomic unit written to disk.
 *
 * On-disk binary format (fixed 25-byte header + variable payload):
 * ┌──────────┬──────────┬────────┬──────────┬─────────┬─────────────────┐
 * │ LSN (8B) │ Term(8B) │Type(1B)│Length(4B)│CRC32(4B)│  Payload (var)  │
 * └──────────┴──────────┴────────┴──────────┴─────────┴─────────────────┘
 *
 * LSN   - Log Sequence Number: monotonically increasing, globally unique
 * Term  - Replication term: detects stale writes from old leaders
 * CRC32 - Checksum over ALL fields including payload: detects disk corruption
 */
public class WALEntry {

    public static final int HEADER_SIZE = 8 + 8 + 1 + 4 + 4; // 25 bytes

    public enum EntryType {
        DATA((byte) 1),
        COMMIT((byte) 2),
        ROLLBACK((byte) 3),
        CHECKPOINT((byte) 4),
        NOOP((byte) 5);

        public final byte code;
        EntryType(byte code) { this.code = code; }

        public static EntryType fromCode(byte code) {
            for (EntryType t : values()) {
                if (t.code == code) return t;
            }
            throw new IllegalArgumentException("Unknown entry type code: " + code);
        }
    }

    private final long lsn;
    private final long term;
    private final EntryType type;
    private final byte[] payload;
    private final long checksum;

    public WALEntry(long lsn, long term, EntryType type, byte[] payload) {
        this.lsn = lsn;
        this.term = term;
        this.type = type;
        this.payload = payload;
        this.checksum = computeChecksum(lsn, term, type, payload);
    }

    // Private constructor used during deserialization (checksum already on disk)
    private WALEntry(long lsn, long term, EntryType type, byte[] payload, long checksum) {
        this.lsn = lsn;
        this.term = term;
        this.type = type;
        this.payload = payload;
        this.checksum = checksum;
    }

    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buf.putLong(lsn);
        buf.putLong(term);
        buf.put(type.code);
        buf.putInt(payload.length);
        buf.putInt((int) checksum);
        buf.put(payload);
        return buf.array();
    }

    public static WALEntry deserialize(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new WALCorruptionException("Entry too short: " + data.length + " bytes");
        }
        ByteBuffer buf = ByteBuffer.wrap(data);
        long lsn           = buf.getLong();
        long term          = buf.getLong();
        EntryType type     = EntryType.fromCode(buf.get());
        int length         = buf.getInt();
        long storedCrc     = Integer.toUnsignedLong(buf.getInt());

        if (data.length < HEADER_SIZE + length) {
            throw new WALCorruptionException("Truncated entry at LSN " + lsn);
        }
        byte[] payload = new byte[length];
        buf.get(payload);

        long computed = computeChecksum(lsn, term, type, payload);
        if (computed != storedCrc) {
            throw new WALCorruptionException(
                String.format("CRC mismatch at LSN %d: stored=%d computed=%d", lsn, storedCrc, computed));
        }
        return new WALEntry(lsn, term, type, payload, storedCrc);
    }

    private static long computeChecksum(long lsn, long term, EntryType type, byte[] payload) {
        CRC32 crc = new CRC32();
        ByteBuffer hdr = ByteBuffer.allocate(8 + 8 + 1 + 4);
        hdr.putLong(lsn); hdr.putLong(term); hdr.put(type.code); hdr.putInt(payload.length);
        crc.update(hdr.array());
        crc.update(payload);
        return crc.getValue();
    }

    public boolean isValid() {
        return computeChecksum(lsn, term, type, payload) == checksum;
    }

    public long getLsn()       { return lsn; }
    public long getTerm()      { return term; }
    public EntryType getType() { return type; }
    public byte[] getPayload() { return payload; }
    public long getChecksum()  { return checksum; }

    @Override
    public String toString() {
        return String.format("WALEntry{lsn=%d, term=%d, type=%s, payloadSize=%d}",
            lsn, term, type, payload.length);
    }
}
