package com.walengine;

import com.walengine.core.*;
import com.walengine.model.WALCorruptionException;
import com.walengine.model.WALEntry;
import com.walengine.recovery.CrashRecovery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class WALEngineTest {

    @TempDir Path tempDir;

    // ════════════════════════════════════════════════════════════════════════
    // WALEntry — Binary Format & CRC32
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Entry serializes and deserializes with same content")
    void entry_serializeDeserialize_roundTrip() {
        byte[] payload = "hello WAL engine".getBytes();
        WALEntry original = new WALEntry(100L, 1L, WALEntry.EntryType.DATA, payload);

        WALEntry recovered = WALEntry.deserialize(original.serialize());

        assertAll(
            () -> assertEquals(original.getLsn(),  recovered.getLsn()),
            () -> assertEquals(original.getTerm(), recovered.getTerm()),
            () -> assertEquals(original.getType(), recovered.getType()),
            () -> assertArrayEquals(original.getPayload(), recovered.getPayload())
        );
    }

    @Test
    @DisplayName("Corrupted byte in payload triggers CRC mismatch exception")
    void entry_corruptedByte_throwsChecksumException() {
        WALEntry entry = new WALEntry(1L, 1L, WALEntry.EntryType.DATA, "test data".getBytes());
        byte[] bytes = entry.serialize();
        bytes[bytes.length - 1] ^= 0xFF; // Flip last byte

        assertThrows(WALCorruptionException.class, () -> WALEntry.deserialize(bytes));
    }

    @Test
    @DisplayName("All entry types serialize and deserialize correctly")
    void entry_allEntryTypes_roundTrip() {
        for (WALEntry.EntryType type : WALEntry.EntryType.values()) {
            WALEntry e = new WALEntry(1L, 1L, type, new byte[0]);
            WALEntry r = WALEntry.deserialize(e.serialize());
            assertEquals(type, r.getType(), "Type mismatch for " + type);
        }
    }

    @Test
    @DisplayName("Large payload (1MB) serializes correctly")
    void entry_largePayload_serializesCorrectly() {
        byte[] big = new byte[1024 * 1024];
        new Random().nextBytes(big);
        WALEntry e = new WALEntry(999L, 1L, WALEntry.EntryType.DATA, big);
        WALEntry r = WALEntry.deserialize(e.serialize());
        assertArrayEquals(big, r.getPayload());
    }

    // ════════════════════════════════════════════════════════════════════════
    // LSNGenerator — Monotonicity & Thread Safety
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("LSNs are strictly monotonically increasing")
    void lsn_strictlyMonotonic() {
        LSNGenerator gen = new LSNGenerator();
        long prev = 0;
        for (int i = 0; i < 1000; i++) {
            long lsn = gen.next();
            assertTrue(lsn > prev, "LSN " + lsn + " not > " + prev);
            prev = lsn;
        }
    }

    @Test
    @DisplayName("No duplicate LSNs across 10 concurrent threads")
    void lsn_noDuplicates_underConcurrency() throws InterruptedException {
        LSNGenerator gen = new LSNGenerator();
        int threads = 10, perThread = 500;
        ConcurrentSkipListSet<Long> all = new ConcurrentSkipListSet<>();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < perThread; j++) all.add(gen.next());
                latch.countDown();
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);
        assertEquals(threads * perThread, all.size(), "Found duplicate LSNs!");
    }

    @Test
    @DisplayName("After restore(), new LSNs are > restored value")
    void lsn_restore_preventsReuse() {
        LSNGenerator gen = new LSNGenerator();
        long first = gen.next();
        gen.restore(first + 5000);
        assertTrue(gen.next() > first + 5000);
    }

    // ════════════════════════════════════════════════════════════════════════
    // WALSegment — FileChannel I/O & fsync
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Entries are readable after append + fsync")
    void segment_appendFsync_readable() throws IOException {
        WALSegment seg = new WALSegment(0, 0, tempDir.resolve("test.log"), 64 * 1024 * 1024);
        WALEntry e1 = new WALEntry(1L, 1L, WALEntry.EntryType.DATA, "entry1".getBytes());
        WALEntry e2 = new WALEntry(2L, 1L, WALEntry.EntryType.COMMIT, new byte[0]);

        seg.append(e1);
        seg.append(e2);
        seg.fsync();

        List<WALEntry> read = seg.readAll();
        seg.close();

        assertEquals(2, read.size());
        assertEquals(1L, read.get(0).getLsn());
        assertEquals(2L, read.get(1).getLsn());
    }

    @Test
    @DisplayName("Data persists after segment close and reopen — durability test")
    void segment_durability_persistsAcrossClose() throws IOException {
        Path segPath = tempDir.resolve("durability.log");
        WALEntry entry = new WALEntry(42L, 1L, WALEntry.EntryType.DATA, "durable-data".getBytes());

        // Write and close
        try (WALSegment seg = new WALSegment(0, 0, segPath, 64 * 1024 * 1024)) {
            seg.append(entry);
            seg.fsync();
        }

        // Reopen and verify — simulates crash recovery
        try (WALSegment seg = new WALSegment(0, 0, segPath, 64 * 1024 * 1024)) {
            List<WALEntry> entries = seg.readAll();
            assertEquals(1, entries.size());
            assertEquals(42L, entries.get(0).getLsn());
            assertArrayEquals("durable-data".getBytes(), entries.get(0).getPayload());
        }
    }

    @Test
    @DisplayName("Segment throws SegmentFullException when capacity exceeded")
    void segment_full_throwsException() throws IOException {
        // Very small segment — 200 bytes
        WALSegment seg = new WALSegment(0, 0, tempDir.resolve("tiny.log"), 200);
        WALEntry e = new WALEntry(1L, 1L, WALEntry.EntryType.DATA, new byte[100]);
        seg.append(e);

        WALEntry overflow = new WALEntry(2L, 1L, WALEntry.EntryType.DATA, new byte[100]);
        assertThrows(WALSegment.SegmentFullException.class, () -> seg.append(overflow));
        seg.close();
    }

    // ════════════════════════════════════════════════════════════════════════
    // WALWriter — Group Commit
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Append returns a valid LSN after durability")
    void writer_append_returnsLsn() throws Exception {
        LSNGenerator gen = new LSNGenerator();
        try (WALWriter writer = new WALWriter(tempDir, gen)) {
            Long lsn = writer.append("payload".getBytes()).get(5, TimeUnit.SECONDS);
            assertNotNull(lsn);
            assertTrue(lsn > 0);
        }
    }

    @Test
    @DisplayName("Group commit: 100 concurrent writers all get unique LSNs")
    void writer_groupCommit_100Writers_allGetUniqueLsns() throws Exception {
        LSNGenerator gen = new LSNGenerator();
        int writerCount = 100;
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        try (WALWriter writer = new WALWriter(tempDir, gen)) {
            // Submit all writes before any fsync can happen
            for (int i = 0; i < writerCount; i++) {
                futures.add(writer.append(("write-" + i).getBytes()));
            }
            // Wait for all
            for (CompletableFuture<Long> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        long distinctLsns = futures.stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { return -1L; } })
            .distinct().count();

        assertEquals(writerCount, distinctLsns, "All LSNs must be unique");
    }

    @Test
    @DisplayName("Written entries are readable by WALReader")
    void writer_writtenEntries_readableByReader() throws Exception {
        LSNGenerator gen = new LSNGenerator();
        try (WALWriter writer = new WALWriter(tempDir, gen)) {
            writer.append("data1".getBytes()).get(5, TimeUnit.SECONDS);
            writer.append("data2".getBytes()).get(5, TimeUnit.SECONDS);
            writer.append("data3".getBytes()).get(5, TimeUnit.SECONDS);
        }

        WALReader reader = new WALReader(tempDir);
        List<WALEntry> entries = reader.readAll();
        assertEquals(3, entries.size());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Crash Recovery
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Recovery replays all committed entries")
    void recovery_replaysAllCommittedEntries() throws Exception {
        LSNGenerator gen1 = new LSNGenerator();
        try (WALWriter writer = new WALWriter(tempDir, gen1)) {
            writer.append("record-1".getBytes()).get(5, TimeUnit.SECONDS);
            writer.append("record-2".getBytes()).get(5, TimeUnit.SECONDS);
        }

        LSNGenerator gen2 = new LSNGenerator();
        List<WALEntry> recovered = new ArrayList<>();
        new CrashRecovery(tempDir, gen2).recover(recovered::add);

        assertEquals(2, recovered.size());
        assertEquals("record-1", new String(recovered.get(0).getPayload()));
        assertEquals("record-2", new String(recovered.get(1).getPayload()));
    }

    @Test
    @DisplayName("Recovery restores LSNGenerator to prevent LSN reuse")
    void recovery_restoresLsnGenerator() throws Exception {
        LSNGenerator gen1 = new LSNGenerator();
        long lastWrittenLsn;

        try (WALWriter writer = new WALWriter(tempDir, gen1)) {
            lastWrittenLsn = writer.append("data".getBytes()).get(5, TimeUnit.SECONDS);
        }

        // Simulate restart with fresh LSNGenerator
        LSNGenerator gen2 = new LSNGenerator();
        new CrashRecovery(tempDir, gen2).recover(e -> {});

        long newLsn = gen2.next();
        assertTrue(newLsn > lastWrittenLsn,
            "New LSN must be > last written LSN to prevent reuse. " +
            "lastWritten=" + lastWrittenLsn + " new=" + newLsn);
    }

    @Test
    @DisplayName("Recovery with checkpoint only replays entries after checkpoint LSN")
    void recovery_withCheckpoint_replaysOnlyAfterCheckpoint() throws Exception {
        LSNGenerator gen1 = new LSNGenerator();
        try (WALWriter writer = new WALWriter(tempDir, gen1)) {
            writer.append("before-checkpoint".getBytes()).get(5, TimeUnit.SECONDS);
            // Write checkpoint marker manually
            writer.append("ckpt".getBytes(), WALEntry.EntryType.CHECKPOINT, 0L).get(5, TimeUnit.SECONDS);
            writer.append("after-checkpoint".getBytes()).get(5, TimeUnit.SECONDS);
        }

        LSNGenerator gen2 = new LSNGenerator();
        List<WALEntry> recovered = new ArrayList<>();
        CrashRecovery.RecoveryResult result =
            new CrashRecovery(tempDir, gen2).recover(recovered::add);

        // Only entries AFTER checkpoint should be replayed
        assertTrue(result.checkpointLsn() > 0, "Checkpoint should be detected");
        assertTrue(recovered.stream()
            .allMatch(e -> e.getLsn() > result.checkpointLsn()),
            "All recovered entries should be after checkpoint");
    }

    // ════════════════════════════════════════════════════════════════════════
    // WALReader
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("readFrom(lsn) returns only entries with LSN >= lsn")
    void reader_readFrom_filtersCorrectly() throws Exception {
        LSNGenerator gen = new LSNGenerator();
        long pivotLsn;

        try (WALWriter writer = new WALWriter(tempDir, gen)) {
            writer.append("early".getBytes()).get(5, TimeUnit.SECONDS);
            pivotLsn = writer.append("pivot".getBytes()).get(5, TimeUnit.SECONDS);
            writer.append("late".getBytes()).get(5, TimeUnit.SECONDS);
        }

        WALReader reader = new WALReader(tempDir);
        List<WALEntry> after = reader.readFrom(pivotLsn + 1);

        assertTrue(after.stream().allMatch(e -> e.getLsn() > pivotLsn));
        assertEquals(1, after.size());
    }

    @Test
    @DisplayName("Segments discovered in LSN order")
    void reader_discoverSegments_correctOrder() throws Exception {
        LSNGenerator gen = new LSNGenerator();
        // Write enough to trigger segment rotation
        try (WALWriter writer = new WALWriter(tempDir, gen, 512)) { // 512-byte segments
            for (int i = 0; i < 20; i++) {
                writer.append(("entry-" + i).getBytes()).get(5, TimeUnit.SECONDS);
            }
        }

        WALReader reader = new WALReader(tempDir);
        List<java.nio.file.Path> segments = reader.discoverSegments();
        assertTrue(segments.size() > 1, "Multiple segments should exist");

        // Verify LSN order via filename lexicographic order
        List<String> names = segments.stream()
            .map(p -> p.getFileName().toString())
            .toList();
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        assertEquals(sorted, names, "Segments should be in LSN order");
    }
}
