package com.walengine.replication;

import com.walengine.core.LSNGenerator;
import com.walengine.core.WALWriter;
import com.walengine.model.WALEntry;
import com.walengine.replication.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ReplicationClient runs on REPLICA nodes.
 * Connects to the primary's gRPC server, receives WAL entries, and applies them locally.
 * Auto-reconnects with exponential backoff on disconnect.
 */
public class ReplicationClient {

    private static final Logger log = LoggerFactory.getLogger(ReplicationClient.class);

    private final String       replicaId;
    private final String       primaryHost;
    private final int          primaryGrpcPort;
    private final WALWriter    localWriter;
    private final LSNGenerator lsnGenerator;

    private final AtomicLong appliedLsn  = new AtomicLong(0);
    private final AtomicLong currentTerm = new AtomicLong(0);

    private ManagedChannel channel;
    private WALReplicationServiceGrpc.WALReplicationServiceStub        asyncStub;
    private WALReplicationServiceGrpc.WALReplicationServiceBlockingStub blockingStub;

    private final ScheduledExecutorService ackScheduler;
    private final ExecutorService          streamExecutor;
    private volatile boolean               running = false;
    private volatile ReplicaState          state   = ReplicaState.DISCONNECTED;

    private Consumer<WALEntry> entryApplier;

    public ReplicationClient(String replicaId, String primaryHost, int primaryGrpcPort,
                             WALWriter localWriter, LSNGenerator lsnGenerator) {
        this.replicaId       = replicaId;
        this.primaryHost     = primaryHost;
        this.primaryGrpcPort = primaryGrpcPort;
        this.localWriter     = localWriter;
        this.lsnGenerator    = lsnGenerator;

        this.ackScheduler  = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "wal-replica-ack"));
        this.streamExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "wal-replica-stream"));
    }

    public void setEntryApplier(Consumer<WALEntry> applier) {
        this.entryApplier = applier;
    }

    public void connect() {
        running = true;
        state   = ReplicaState.CONNECTING;

        channel = ManagedChannelBuilder
            .forAddress(primaryHost, primaryGrpcPort)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .build();

        asyncStub    = WALReplicationServiceGrpc.newStub(channel);
        blockingStub = WALReplicationServiceGrpc.newBlockingStub(channel);

        streamExecutor.submit(this::connectWithRetry);
        ackScheduler.scheduleAtFixedRate(this::sendAck, 500, 500, TimeUnit.MILLISECONDS);

        log.info("ReplicationClient connecting to {}:{}", primaryHost, primaryGrpcPort);
    }

    private void connectWithRetry() {
        int attempt = 0;
        while (running) {
            try {
                attempt++;
                log.info("Connecting to primary (attempt {})", attempt);
                doHandshake();
                doStreamEntries();
                log.info("Stream ended. Reconnecting...");
            } catch (Exception e) {
                if (!running) break;
                long backoff = Math.min(30_000, 1000L * (1L << Math.min(attempt, 5)));
                log.warn("Connection failed (attempt {}): {}. Retrying in {}ms",
                    attempt, e.getMessage(), backoff);
                try { Thread.sleep(backoff); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private void doHandshake() {
        state = ReplicaState.HANDSHAKING;
        HandshakeResponse response = blockingStub.handshake(
            HandshakeRequest.newBuilder()
                .setReplicaId(replicaId)
                .setCurrentLsn(appliedLsn.get())
                .setReplicaHost("self")
                .build());

        if (!response.getAccepted()) {
            throw new RuntimeException("Handshake rejected: " + response.getMessage());
        }
        log.info("Handshake accepted. PrimaryLsn={} catchUpFrom={}",
            response.getPrimaryLsn(), response.getCatchUpFrom());
    }

    private void doStreamEntries() throws InterruptedException {
        state = ReplicaState.CATCHING_UP;
        CountDownLatch streamDone = new CountDownLatch(1);

        asyncStub.streamEntries(
            HandshakeRequest.newBuilder()
                .setReplicaId(replicaId)
                .setCurrentLsn(appliedLsn.get())
                .build(),
            new StreamObserver<ReplicationStream>() {
                @Override
                public void onNext(ReplicationStream msg) { applyEntry(msg); }

                @Override
                public void onError(Throwable t) {
                    log.error("Stream error: {}", t.getMessage());
                    state = ReplicaState.DISCONNECTED;
                    streamDone.countDown();
                }

                @Override
                public void onCompleted() {
                    log.info("Stream completed by primary");
                    state = ReplicaState.DISCONNECTED;
                    streamDone.countDown();
                }
            });

        streamDone.await();
    }

    private void applyEntry(ReplicationStream msg) {
        WALEntryProto proto = msg.getEntry();

        // Reject stale-term entries (split-brain protection)
        if (proto.getTerm() > 0 && proto.getTerm() < currentTerm.get()) {
            log.warn("Rejecting stale-term entry. entryTerm={} currentTerm={}",
                proto.getTerm(), currentTerm.get());
            return;
        }

        WALEntry entry = new WALEntry(
            proto.getLsn(),
            proto.getTerm(),
            WALEntry.EntryType.fromCode((byte) proto.getEntryType()),
            proto.getPayload().toByteArray()
        );

        // Write to local WAL
        localWriter.append(entry.getPayload(), entry.getType(), entry.getTerm());

        // Notify application
        if (entryApplier != null) entryApplier.accept(entry);

        appliedLsn.set(proto.getLsn());

        long lag = msg.getPrimaryLsn() - proto.getLsn();
        if (lag > 1000) log.warn("Replication lag: {} entries", lag);

        if (state == ReplicaState.CATCHING_UP) state = ReplicaState.STREAMING;
    }

    private void sendAck() {
        if (blockingStub == null || state == ReplicaState.DISCONNECTED) return;
        try {
            blockingStub.acknowledge(AckRequest.newBuilder()
                .setReplicaId(replicaId)
                .setAppliedLsn(appliedLsn.get())
                .build());
        } catch (Exception e) {
            log.debug("Ack failed (will retry): {}", e.getMessage());
        }
    }

    public void disconnect() {
        running = false;
        ackScheduler.shutdown();
        streamExecutor.shutdown();
        if (channel != null) channel.shutdown();
        state = ReplicaState.DISCONNECTED;
        log.info("ReplicationClient disconnected");
    }

    public long getAppliedLsn()    { return appliedLsn.get(); }
    public ReplicaState getState() { return state; }

    public enum ReplicaState {
        DISCONNECTED, CONNECTING, HANDSHAKING, CATCHING_UP, STREAMING, FAILED
    }
}
