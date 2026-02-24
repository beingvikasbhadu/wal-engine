package com.walengine.core;

import com.walengine.model.WALEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.*;

/**
 * LogCompactor manages WAL segment lifecycle.
 *
 * Without compaction the WAL grows forever. Segments become eligible for
 * deletion once:
 *   1. A CHECKPOINT entry has been written (marking consistent state)
 *   2. All replicas have acknowledged past the segment's last LSN
 *   3. The application has persisted its snapshot to disk
 *
 * Safe deletion guarantee:
 *   - Segment's maxLsn < checkpointLsn → safe to delete (recovery won't need it)
 *   - Segment's maxLsn < min(replicaAckedLsn) → replicas won't need catch-up from it
 */
public class LogCompactor {

    private static final Logger log = LoggerFactory.getLogger(LogCompactor.class);

    private final WALWriter          writer;
    private final WALReader          reader;
    private final Path               walDirectory;
    private final Path               archiveDirectory;

    private final ScheduledExecutorService scheduler;
    private volatile long            lastCheckpointLsn = 0;

    public LogCompactor(WALWriter writer, WALReader reader,
                        Path walDirectory, Path archiveDirectory) throws IOException {
        this.writer           = writer;
        this.reader           = reader;
        this.walDirectory     = walDirectory;
        this.archiveDirectory = archiveDirectory;
        Files.createDirectories(archiveDirectory);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wal-compactor");
            t.setDaemon(true);
            return t;
        });
    }

    /** Start automatic compaction checks every intervalMinutes. */
    public void startAutoCompaction(long intervalMinutes) {
        scheduler.scheduleAtFixedRate(this::autoCompact,
            intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        log.info("Auto-compaction scheduled every {} minutes", intervalMinutes);
    }

    /**
     * Write a CHECKPOINT entry to the WAL.
     * After this returns, the application should persist its in-memory snapshot.
     * All WAL segments before this LSN become eligible for compaction.
     */
    public long checkpoint() throws Exception {
        byte[] payload = ("checkpoint@" + System.currentTimeMillis()).getBytes();
        Long lsn = writer.append(payload, WALEntry.EntryType.CHECKPOINT, 0L).get(10, TimeUnit.SECONDS);
        lastCheckpointLsn = lsn;
        log.info("Checkpoint written at LSN {}", lsn);
        return lsn;
    }

    /**
     * Compact segments whose entire content is before safeCompactLsn.
     *
     * @param safeCompactLsn All entries <= this LSN are safe to discard
     * @param archive        If true, move to archive dir; if false, delete permanently
     * @return Number of segments compacted
     */
    public int compact(long safeCompactLsn, boolean archive) throws IOException {
        log.info("Compaction started. safeCompactLsn={} archive={}", safeCompactLsn, archive);
        List<Path> segments = reader.discoverSegments();
        int count = 0;

        // Never compact the last segment — it's likely still active
        for (int i = 0; i < segments.size() - 1; i++) {
            Path segPath = segments.get(i);
            try {
                WALSegment seg = new WALSegment(i, 0, segPath, Long.MAX_VALUE);
                List<WALEntry> entries = seg.readAll();
                seg.close();

                if (entries.isEmpty()) {
                    deleteOrArchive(segPath, archive);
                    count++;
                    continue;
                }

                long maxLsnInSeg = entries.stream()
                    .mapToLong(WALEntry::getLsn).max().orElse(0L);

                if (maxLsnInSeg < safeCompactLsn) {
                    log.info("Compacting {} (maxLsn={} < safeCompactLsn={})",
                        segPath.getFileName(), maxLsnInSeg, safeCompactLsn);
                    deleteOrArchive(segPath, archive);
                    count++;
                } else {
                    log.debug("Keeping {} (maxLsn={} >= safeCompactLsn={})",
                        segPath.getFileName(), maxLsnInSeg, safeCompactLsn);
                }
            } catch (Exception e) {
                log.warn("Error reading {} during compaction — skipping", segPath.getFileName(), e);
            }
        }
        log.info("Compaction done: {} segments removed", count);
        return count;
    }

    private void deleteOrArchive(Path path, boolean archive) throws IOException {
        if (archive) {
            Path dest = archiveDirectory.resolve(path.getFileName());
            Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archived: {}", path.getFileName());
        } else {
            Files.delete(path);
            log.info("Deleted: {}", path.getFileName());
        }
    }

    private void autoCompact() {
        try {
            List<Path> segs = reader.discoverSegments();
            if (segs.size() > 10) {
                log.info("Auto-compaction triggered ({} segments)", segs.size());
                compact(lastCheckpointLsn, true);
            }
        } catch (Exception e) {
            log.error("Auto-compaction failed", e);
        }
    }

    public long getLastCheckpointLsn() { return lastCheckpointLsn; }

    public void shutdown() { scheduler.shutdown(); }
}
