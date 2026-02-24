package com.walengine.core;

import com.walengine.model.WALCorruptionException;
import com.walengine.model.WALEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * WALReader scans WAL segment files and iterates entries.
 *
 * Used by:
 *   - CrashRecovery: replay committed entries after restart
 *   - ReplicationServer: send missed entries to catching-up replicas
 *   - LogCompactor: identify segments safe for deletion
 */
public class WALReader {

    private static final Logger log = LoggerFactory.getLogger(WALReader.class);

    private final Path walDirectory;

    public WALReader(Path walDirectory) {
        this.walDirectory = walDirectory;
    }

    /** Read ALL entries across all segments, in LSN order. */
    public List<WALEntry> readAll() throws IOException {
        List<Path> segFiles = discoverSegments();
        List<WALEntry> result = new ArrayList<>();

        for (Path segFile : segFiles) {
            try {
                WALSegment seg = openForReading(segFile);
                List<WALEntry> entries = seg.readAll();
                seg.close();
                result.addAll(entries);
                log.debug("Read {} entries from {}", entries.size(), segFile.getFileName());
            } catch (WALCorruptionException e) {
                log.warn("Corruption at segment {} — stopping scan: {}", segFile.getFileName(), e.getMessage());
                break; // Stop at first corruption — entries beyond are unrecoverable
            }
        }
        return result;
    }

    /** Read all entries with LSN >= fromLsn. Used by replication catch-up. */
    public List<WALEntry> readFrom(long fromLsn) throws IOException {
        return readAll().stream()
            .filter(e -> e.getLsn() >= fromLsn)
            .collect(Collectors.toList());
    }

    /**
     * Read only DATA entries that are not followed by a ROLLBACK.
     * Simplified model: in production, transaction IDs would be tracked separately.
     */
    public List<WALEntry> readCommitted() throws IOException {
        List<WALEntry> all = readAll();
        Set<Long> rolledBack = all.stream()
            .filter(e -> e.getType() == WALEntry.EntryType.ROLLBACK)
            .map(WALEntry::getLsn)
            .collect(Collectors.toSet());

        return all.stream()
            .filter(e -> e.getType() == WALEntry.EntryType.DATA)
            .filter(e -> !rolledBack.contains(e.getLsn()))
            .collect(Collectors.toList());
    }

    /** Find the highest LSN across all segments. Used to restore LSNGenerator after crash. */
    public long findLastLsn() throws IOException {
        return readAll().stream().mapToLong(WALEntry::getLsn).max().orElse(0L);
    }

    /** Find the last CHECKPOINT entry. Recovery only replays entries after this. */
    public Optional<WALEntry> findLastCheckpoint() throws IOException {
        return readAll().stream()
            .filter(e -> e.getType() == WALEntry.EntryType.CHECKPOINT)
            .max(Comparator.comparingLong(WALEntry::getLsn));
    }

    /**
     * Discover all WAL segment files sorted by name (which is LSN-order due to naming scheme).
     * File pattern: wal-{startLsn:020d}-{segId:06d}.log
     */
    public List<Path> discoverSegments() throws IOException {
        if (!Files.exists(walDirectory)) return List.of();
        try (Stream<Path> files = Files.list(walDirectory)) {
            return files
                .filter(p -> p.getFileName().toString().matches("wal-\\d{20}-\\d{6}\\.log"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());
        }
    }

    private WALSegment openForReading(Path path) throws IOException {
        // Parse: wal-{lsn}-{segId}.log
        String name = path.getFileName().toString().replace("wal-", "").replace(".log", "");
        String[] parts = name.split("-");
        long startLsn = Long.parseLong(parts[0]);
        long segId    = Long.parseLong(parts[1]);
        return new WALSegment(segId, startLsn, path, Long.MAX_VALUE);
    }
}
