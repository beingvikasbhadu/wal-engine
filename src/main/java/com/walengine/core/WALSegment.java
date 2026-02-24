package com.walengine.core;

import com.walengine.model.WALCorruptionException;
import com.walengine.model.WALEntry;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WALSegment manages one log file on disk.
 *
 * Write layout per entry: [4-byte length prefix][serialized WALEntry bytes]
 *
 * The 4-byte length prefix lets us know how many bytes to read for the next entry
 * during sequential scans. This pattern (length-prefixed frames) is used by
 * Kafka, Postgres WAL, and most other log-structured systems.
 *
 * Critical: FileChannel.force(true) (fsync) flushes OS page cache to physical
 * disk. Without this, data lives only in kernel memory and is lost on power failure.
 */
public class WALSegment implements Closeable {

    public static final int LENGTH_PREFIX = 4;

    private final long segmentId;
    private final long startLsn;
    private final Path filePath;
    private final long maxSizeBytes;

    private final FileChannel channel;
    private volatile long currentSize;
    private volatile long flushedPosition;
    private volatile boolean closed = false;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public WALSegment(long segmentId, long startLsn, Path filePath, long maxSizeBytes)
            throws IOException {
        this.segmentId    = segmentId;
        this.startLsn     = startLsn;
        this.filePath     = filePath;
        this.maxSizeBytes = maxSizeBytes;
        this.channel = FileChannel.open(filePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE);
        this.currentSize   = channel.size();
        this.flushedPosition = currentSize;
    }

    /**
     * Append one entry. Returns byte offset at which entry was written.
     * Does NOT fsync — caller must call fsync() explicitly.
     */
    public long append(WALEntry entry) throws IOException {
        if (closed) throw new IllegalStateException("Segment is closed");
        byte[] serialized = entry.serialize();
        int total = LENGTH_PREFIX + serialized.length;

        lock.writeLock().lock();
        try {
            if (currentSize + total > maxSizeBytes) {
                throw new SegmentFullException("Segment " + segmentId + " full");
            }
            long offset = currentSize;
            ByteBuffer buf = ByteBuffer.allocate(total);
            buf.putInt(serialized.length);
            buf.put(serialized);
            buf.flip();
            channel.write(buf, offset);
            currentSize += total;
            return offset;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * fsync: flush OS page cache → physical disk.
     * This is ~1-10ms on SSDs. We call force(true) to also flush file metadata.
     * After this returns, data is guaranteed durable even on power loss.
     */
    public void fsync() throws IOException {
        lock.writeLock().lock();
        try {
            channel.force(true);
            flushedPosition = currentSize;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Read all entries starting at byteOffset.
     * Handles partial tail writes (crash mid-write) gracefully by stopping early.
     */
    public List<WALEntry> readFrom(long byteOffset) throws IOException {
        lock.readLock().lock();
        try {
            List<WALEntry> entries = new ArrayList<>();
            long pos = byteOffset;

            while (pos < currentSize) {
                // Read 4-byte length prefix
                ByteBuffer lenBuf = ByteBuffer.allocate(LENGTH_PREFIX);
                if (channel.read(lenBuf, pos) < LENGTH_PREFIX) break; // truncated
                lenBuf.flip();
                int entryLen = lenBuf.getInt();

                if (entryLen <= 0 || entryLen > 64 * 1024 * 1024) {
                    throw new WALCorruptionException("Invalid entry length " + entryLen + " at offset " + pos);
                }

                ByteBuffer entryBuf = ByteBuffer.allocate(entryLen);
                if (channel.read(entryBuf, pos + LENGTH_PREFIX) < entryLen) break; // partial write at tail

                entries.add(WALEntry.deserialize(entryBuf.array()));
                pos += LENGTH_PREFIX + entryLen;
            }
            return entries;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<WALEntry> readAll() throws IOException { return readFrom(0); }

    /** Truncate at byteOffset — used by replica when discarding stale-term entries. */
    public void truncateAt(long byteOffset) throws IOException {
        lock.writeLock().lock();
        try {
            channel.truncate(byteOffset);
            currentSize = byteOffset;
            flushedPosition = Math.min(flushedPosition, byteOffset);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isFull()              { return currentSize >= maxSizeBytes; }
    public long getCurrentSize()         { return currentSize; }
    public long getSegmentId()           { return segmentId; }
    public long getStartLsn()            { return startLsn; }
    public Path getFilePath()            { return filePath; }
    public long getFlushedPosition()     { return flushedPosition; }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            fsync();
            channel.close();
        }
    }

    public static class SegmentFullException extends IOException {
        public SegmentFullException(String msg) { super(msg); }
    }
}
