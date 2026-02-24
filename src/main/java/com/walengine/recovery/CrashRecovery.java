package com.walengine.recovery;

import com.walengine.core.LSNGenerator;
import com.walengine.core.WALReader;
import com.walengine.model.WALEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * CrashRecovery replays the WAL on startup after an unclean shutdown.
 *
 * Recovery protocol (same as PostgreSQL crash recovery):
 * ────────────────────────────────────────────────────────
 * 1. Find last CHECKPOINT entry → marks where a consistent snapshot exists
 * 2. Replay all DATA entries AFTER that checkpoint LSN
 * 3. Stop at first corrupt/truncated entry (partial write at crash time)
 * 4. Restore LSNGenerator to lastLsn+1 → prevents LSN reuse after restart
 *
 * Why step 4 matters: If the system crashed at LSN=500 and we restart without
 * restoring, the LSNGenerator starts at 0 again. New entries would get LSNs
 * already present in the WAL → replicas would silently overwrite old data.
 */
public class CrashRecovery {

    private static final Logger log = LoggerFactory.getLogger(CrashRecovery.class);

    private final Path walDirectory;
    private final LSNGenerator lsnGenerator;

    public CrashRecovery(Path walDirectory, LSNGenerator lsnGenerator) {
        this.walDirectory = walDirectory;
        this.lsnGenerator = lsnGenerator;
    }

    /**
     * Run recovery. Calls entryHandler for each recovered entry in LSN order.
     * The application should apply these to restore in-memory state.
     *
     * @return RecoveryResult with stats (entries replayed, last LSN, duration)
     */
    public RecoveryResult recover(Consumer<WALEntry> entryHandler) throws IOException {
        log.info("=== CRASH RECOVERY STARTED from {} ===", walDirectory);
        long t0 = System.currentTimeMillis();

        WALReader reader = new WALReader(walDirectory);

        // Step 1: Find last checkpoint
        Optional<WALEntry> checkpoint = reader.findLastCheckpoint();
        long checkpointLsn = checkpoint.map(WALEntry::getLsn).orElse(0L);
        log.info("Last checkpoint LSN: {} ({})",
            checkpointLsn, checkpointLsn == 0 ? "none found — full replay" : "replaying from here");

        // Step 2: Read all entries
        List<WALEntry> all = reader.readAll();
        long lastLsn = all.isEmpty() ? 0L : all.get(all.size() - 1).getLsn();
        log.info("Total entries in WAL: {}. Last LSN: {}", all.size(), lastLsn);

        // Step 3: Replay DATA entries after checkpoint
        List<WALEntry> toReplay = all.stream()
            .filter(e -> e.getLsn() > checkpointLsn)
            .filter(e -> e.getType() == WALEntry.EntryType.DATA ||
                         e.getType() == WALEntry.EntryType.COMMIT)
            .toList();

        int replayed = 0;
        long lastReplayed = checkpointLsn;

        for (WALEntry entry : toReplay) {
            try {
                entryHandler.accept(entry);
                lastReplayed = entry.getLsn();
                replayed++;
            } catch (Exception e) {
                log.error("Failed to apply entry LSN={}: {}", entry.getLsn(), e.getMessage());
                // Lenient mode: log and continue. Strict mode would throw here.
            }
        }

        // Step 4: Restore LSNGenerator — CRITICAL to prevent LSN reuse
        if (lastLsn > 0) {
            lsnGenerator.restore(lastLsn);
            log.info("LSNGenerator restored to LSN {}", lastLsn);
        }

        long elapsed = System.currentTimeMillis() - t0;
        RecoveryResult result = new RecoveryResult(replayed, lastReplayed, checkpointLsn, elapsed);
        log.info("=== CRASH RECOVERY COMPLETE: {} ===", result);
        return result;
    }

    public record RecoveryResult(
        int  entriesReplayed,
        long lastReplayedLsn,
        long checkpointLsn,
        long durationMs
    ) {
        @Override public String toString() {
            return String.format(
                "RecoveryResult{replayed=%d, lastLsn=%d, checkpointLsn=%d, durationMs=%d}",
                entriesReplayed, lastReplayedLsn, checkpointLsn, durationMs);
        }
    }
}
