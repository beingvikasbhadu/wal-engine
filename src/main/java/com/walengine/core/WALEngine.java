package com.walengine.core;

import com.walengine.model.WALEntry;
import com.walengine.recovery.CrashRecovery;
import com.walengine.replication.ReplicationClient;
import com.walengine.replication.ReplicationServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WALEngine is the single public API for this library.
 *
 * Usage:
 * ──────
 *   // Primary node
 *   WALEngine engine = WALEngine.builder()
 *       .walDirectory(Path.of("/data/wal"))
 *       .role(NodeRole.PRIMARY)
 *       .grpcPort(9090)
 *       .build();
 *
 *   engine.recover(entry -> myApp.apply(entry)); // Always call on startup
 *
 *   Long lsn = engine.append("my data".getBytes()).get(); // Durable write
 *   engine.commit().get();
 *   engine.checkpoint();                                   // Enables compaction
 *
 *   // Replica node
 *   WALEngine replica = WALEngine.builder()
 *       .walDirectory(Path.of("/data/wal-replica"))
 *       .role(NodeRole.REPLICA)
 *       .primaryHost("primary-host")
 *       .primaryGrpcPort(9090)
 *       .build();
 *
 *   replica.startReplication(entry -> myApp.apply(entry));
 */
public class WALEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WALEngine.class);

    private final WALWriter         writer;
    private final WALReader         reader;
    private final LogCompactor      compactor;
    private final CrashRecovery     crashRecovery;
    private final LSNGenerator      lsnGenerator;
    private final NodeRole          role;
    private final AtomicLong        currentTerm = new AtomicLong(1);

    // PRIMARY only
    private ReplicationServer replicationServer;

    // REPLICA only
    private ReplicationClient replicationClient;

    private WALEngine(Builder b) throws IOException {
        this.lsnGenerator = new LSNGenerator();
        this.role         = b.role;

        this.writer = new WALWriter(b.walDirectory, lsnGenerator, b.segmentSizeBytes);
        this.reader = new WALReader(b.walDirectory);
        this.compactor = new LogCompactor(
            writer, reader, b.walDirectory, b.walDirectory.resolve("archive"));
        this.crashRecovery = new CrashRecovery(b.walDirectory, lsnGenerator);

        if (role == NodeRole.PRIMARY && b.grpcPort > 0) {
            this.replicationServer = new ReplicationServer(
                writer, reader, b.grpcPort, currentTerm);
            this.replicationServer.start();
        }

        if (role == NodeRole.REPLICA) {
            this.replicationClient = new ReplicationClient(
                b.replicaId, b.primaryHost, b.primaryGrpcPort, writer, lsnGenerator);
        }

        if (b.enableAutoCompaction) {
            compactor.startAutoCompaction(30);
        }

        log.info("WALEngine started. role={} dir={}", role, b.walDirectory);
    }

    // ─── Core Write API ───────────────────────────────────────────────────────

    /** Append a DATA entry. Completes with LSN after fsync. */
    public CompletableFuture<Long> append(byte[] payload) {
        return writer.append(payload, WALEntry.EntryType.DATA, currentTerm.get());
    }

    /** Write a COMMIT marker. */
    public CompletableFuture<Long> commit() {
        return writer.append(new byte[0], WALEntry.EntryType.COMMIT, currentTerm.get());
    }

    /** Write a ROLLBACK marker. */
    public CompletableFuture<Long> rollback() {
        return writer.append(new byte[0], WALEntry.EntryType.ROLLBACK, currentTerm.get());
    }

    // ─── Recovery ─────────────────────────────────────────────────────────────

    /** Must be called on startup. Replays committed entries since last checkpoint. */
    public CrashRecovery.RecoveryResult recover(Consumer<WALEntry> handler) throws IOException {
        return crashRecovery.recover(handler);
    }

    // ─── Checkpointing & Compaction ───────────────────────────────────────────

    /** Write a CHECKPOINT entry. Call this after persisting application snapshot. */
    public long checkpoint() throws Exception {
        return compactor.checkpoint();
    }

    /**
     * Compact segments older than the last checkpoint.
     * Safe to call only after checkpoint() + application snapshot are complete.
     */
    public int compact(boolean archive) throws IOException {
        return compactor.compact(compactor.getLastCheckpointLsn(), archive);
    }

    // ─── Replication ──────────────────────────────────────────────────────────

    /**
     * Start receiving entries from primary (REPLICA mode only).
     * @param entryApplier called for each received entry, in LSN order
     */
    public void startReplication(Consumer<WALEntry> entryApplier) {
        if (role != NodeRole.REPLICA) {
            throw new IllegalStateException("startReplication() is only valid on REPLICA nodes");
        }
        replicationClient.setEntryApplier(entryApplier);
        replicationClient.connect();
        log.info("Replication started");
    }

    /** Replication lag per replica (PRIMARY mode only). */
    public Map<String, Long> getReplicationLag() {
        if (replicationServer == null) return Map.of();
        return replicationServer.getReplicaLag();
    }

    // ─── Read / Inspect ───────────────────────────────────────────────────────

    public List<WALEntry> readCommitted() throws IOException { return reader.readCommitted(); }
    public long getCurrentLsn()         { return lsnGenerator.getLastLsn(); }
    public NodeRole getRole()           { return role; }
    public WALWriter getWriter()        { return writer; }
    public WALReader getReader()        { return reader; }

    @Override
    public void close() throws Exception {
        log.info("Shutting down WALEngine...");
        if (replicationClient != null) replicationClient.disconnect();
        if (replicationServer != null) replicationServer.stop();
        compactor.shutdown();
        writer.close();
        log.info("WALEngine closed.");
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public enum NodeRole { PRIMARY, REPLICA }

    public static class Builder {
        private Path     walDirectory;
        private long     segmentSizeBytes    = 64L * 1024 * 1024;
        private NodeRole role                = NodeRole.PRIMARY;
        private int      grpcPort            = 9090;
        private String   primaryHost         = "localhost";
        private int      primaryGrpcPort     = 9090;
        private String   replicaId           = "replica-" + System.currentTimeMillis();
        private boolean  enableAutoCompaction = true;

        public Builder walDirectory(Path d)         { walDirectory = d;         return this; }
        public Builder segmentSizeBytes(long s)     { segmentSizeBytes = s;     return this; }
        public Builder role(NodeRole r)             { role = r;                 return this; }
        public Builder grpcPort(int p)              { grpcPort = p;             return this; }
        public Builder primaryHost(String h)        { primaryHost = h;          return this; }
        public Builder primaryGrpcPort(int p)       { primaryGrpcPort = p;      return this; }
        public Builder replicaId(String id)         { replicaId = id;           return this; }
        public Builder enableAutoCompaction(boolean e) { enableAutoCompaction = e; return this; }

        public WALEngine build() throws IOException {
            if (walDirectory == null) throw new IllegalStateException("walDirectory is required");
            return new WALEngine(this);
        }
    }
}
