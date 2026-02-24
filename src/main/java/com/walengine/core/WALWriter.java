package com.walengine.core;

import com.walengine.model.WALEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WALWriter is the central write path.
 *
 * ═══ GROUP COMMIT ═══════════════════════════════════════════════════════════
 * fsync is expensive (~1-10ms). If each write triggered its own fsync,
 * max throughput = 1000/1ms = ~1000 writes/sec.
 *
 * Group commit batches concurrent writes into one fsync:
 *   1. N threads call append() and each gets a CompletableFuture<Long>
 *   2. A dedicated commit thread drains the queue, writes all N entries
 *   3. ONE fsync call covers the entire batch
 *   4. All N futures complete simultaneously → all N threads unblock
 *
 * Result: 10,000–100,000 writes/sec on the same hardware.
 * This is exactly how PostgreSQL's walwriter background process works.
 * ════════════════════════════════════════════════════════════════════════════
 */
public class WALWriter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WALWriter.class);

    private static final long DEFAULT_SEGMENT_SIZE = 64L * 1024 * 1024; // 64 MB
    private static final int  BATCH_MAX            = 2000;
    private static final long COMMIT_INTERVAL_MS   = 5;

    private final Path walDirectory;
    private final LSNGenerator lsnGenerator;
    private final long segmentSizeBytes;

    private final List<WALSegment>      allSegments    = new CopyOnWriteArrayList<>();
    private volatile WALSegment         activeSegment;
    private final AtomicLong            segmentCounter = new AtomicLong(0);

    private final BlockingQueue<PendingWrite> queue  = new LinkedBlockingQueue<>();
    private final ExecutorService        commitThread;
    private volatile boolean             running = true;

    private volatile ReplicationNotifier replicationNotifier;

    public WALWriter(Path walDirectory, LSNGenerator lsnGenerator) throws IOException {
        this(walDirectory, lsnGenerator, DEFAULT_SEGMENT_SIZE);
    }

    public WALWriter(Path walDirectory, LSNGenerator lsnGenerator, long segmentSizeBytes)
            throws IOException {
        this.walDirectory    = walDirectory;
        this.lsnGenerator    = lsnGenerator;
        this.segmentSizeBytes = segmentSizeBytes;

        Files.createDirectories(walDirectory);
        this.activeSegment = createSegment();

        this.commitThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wal-commit");
            t.setDaemon(false); // Must finish all commits before JVM exits
            return t;
        });
        commitThread.submit(this::commitLoop);
        log.info("WALWriter started. dir={} segmentSize={}MB",
            walDirectory, segmentSizeBytes / 1024 / 1024);
    }

    /**
     * Append payload to WAL. Returns a Future that completes with the assigned LSN
     * ONLY AFTER the entry is durably on disk (post-fsync).
     *
     * Callers MUST await this future before acknowledging writes to clients.
     */
    public CompletableFuture<Long> append(byte[] payload) {
        return append(payload, WALEntry.EntryType.DATA, 0L);
    }

    public CompletableFuture<Long> append(byte[] payload, WALEntry.EntryType type, long term) {
        if (!running) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WALWriter is shut down"));
        }
        long lsn = lsnGenerator.next();
        WALEntry entry = new WALEntry(lsn, term, type, payload);
        CompletableFuture<Long> future = new CompletableFuture<>();
        queue.offer(new PendingWrite(entry, future));
        return future;
    }

    // ─── Commit Loop ─────────────────────────────────────────────────────────

    private void commitLoop() {
        log.info("Commit loop started");
        while (running || !queue.isEmpty()) {
            try {
                List<PendingWrite> batch = new ArrayList<>();

                // Block until at least one write, then drain everything available
                PendingWrite first = queue.poll(COMMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, BATCH_MAX - 1);
                }
                if (batch.isEmpty()) continue;

                // Write all entries to FileChannel (still in OS memory — no fsync yet)
                List<WALEntry> committed = new ArrayList<>();
                for (PendingWrite pw : batch) {
                    try {
                        ensureCapacity(pw.entry);
                        activeSegment.append(pw.entry);
                        committed.add(pw.entry);
                    } catch (IOException e) {
                        pw.future.completeExceptionally(e);
                    }
                }

                // ← ONE fsync for the whole batch — this is the key to group commit
                activeSegment.fsync();

                // Everything is durable — complete all futures
                for (PendingWrite pw : batch) {
                    if (!pw.future.isCompletedExceptionally()) {
                        pw.future.complete(pw.entry.getLsn());
                    }
                }

                // Notify replication layer (async, non-blocking)
                if (replicationNotifier != null && !committed.isEmpty()) {
                    replicationNotifier.notify(committed);
                }

                log.debug("Batch committed: size={} lastLsn={}",
                    batch.size(), batch.get(batch.size() - 1).entry.getLsn());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                log.error("Fatal I/O error in commit loop", e);
                queue.forEach(pw -> pw.future.completeExceptionally(e));
                break;
            }
        }
        log.info("Commit loop stopped");
    }

    // ─── Segment Management ───────────────────────────────────────────────────

    private void ensureCapacity(WALEntry entry) throws IOException {
        int needed = WALSegment.LENGTH_PREFIX + WALEntry.HEADER_SIZE + entry.getPayload().length;
        if (activeSegment.getCurrentSize() + needed > segmentSizeBytes) {
            rotateSegment();
        }
    }

    private synchronized void rotateSegment() throws IOException {
        log.info("Rotating segment id={} size={}MB",
            activeSegment.getSegmentId(),
            activeSegment.getCurrentSize() / 1024 / 1024);
        activeSegment.close();
        activeSegment = createSegment();
    }

    private WALSegment createSegment() throws IOException {
        long id  = segmentCounter.getAndIncrement();
        long lsn = lsnGenerator.getLastLsn();
        // Name format ensures lexicographic order == LSN order
        Path path = walDirectory.resolve(
            String.format("wal-%020d-%06d.log", lsn, id));
        WALSegment seg = new WALSegment(id, lsn, path, segmentSizeBytes);
        allSegments.add(seg);
        return seg;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public void setReplicationNotifier(ReplicationNotifier n) { this.replicationNotifier = n; }
    public List<WALSegment> getSegments()   { return new ArrayList<>(allSegments); }
    public WALSegment getActiveSegment()    { return activeSegment; }
    public long getLastLsn()               { return lsnGenerator.getLastLsn(); }

    @Override
    public void close() throws Exception {
        log.info("Shutting down WALWriter...");
        running = false;
        commitThread.shutdown();
        if (!commitThread.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Commit thread did not stop cleanly within 30s");
        }
        activeSegment.close();
        log.info("WALWriter closed.");
    }

    private record PendingWrite(WALEntry entry, CompletableFuture<Long> future) {}

    @FunctionalInterface
    public interface ReplicationNotifier {
        void notify(List<WALEntry> entries);
    }
}
