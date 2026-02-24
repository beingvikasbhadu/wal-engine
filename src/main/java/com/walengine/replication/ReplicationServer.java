package com.walengine.replication;

import com.walengine.core.WALReader;
import com.walengine.core.WALWriter;
import com.walengine.model.WALEntry;
import com.walengine.replication.proto.*;
import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReplicationServer runs on the PRIMARY node.
 * Starts a gRPC server that replicas connect to for streaming replication.
 * Equivalent to PostgreSQL's walsender process.
 */
public class ReplicationServer {

    private static final Logger log = LoggerFactory.getLogger(ReplicationServer.class);

    private final WALWriter  walWriter;
    private final WALReader  walReader;
    private final int        grpcPort;
    private final AtomicLong currentTerm;

    private final Map<String, BlockingQueue<WALEntry>> replicaQueues   = new ConcurrentHashMap<>();
    private final Map<String, Long>                    replicaAckedLsn = new ConcurrentHashMap<>();

    private Server grpcServer;

    public ReplicationServer(WALWriter walWriter, WALReader walReader,
                             int grpcPort, AtomicLong currentTerm) {
        this.walWriter   = walWriter;
        this.walReader   = walReader;
        this.grpcPort    = grpcPort;
        this.currentTerm = currentTerm;
        walWriter.setReplicationNotifier(this::notifyNewEntries);
    }

    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(grpcPort)
            .addService(new WALReplicationServiceImpl())
            .build()
            .start();
        log.info("gRPC Replication server started on port {}", grpcPort);
    }

    public void stop() {
        if (grpcServer != null) {
            grpcServer.shutdown();
            log.info("gRPC Replication server stopped");
        }
    }

    public void notifyNewEntries(List<WALEntry> entries) {
        replicaQueues.forEach((replicaId, queue) -> {
            for (WALEntry e : entries) {
                if (!queue.offer(e)) {
                    log.warn("Replica {} queue full — LSN={} dropped", replicaId, e.getLsn());
                }
            }
        });
    }

    public Map<String, Long> getReplicaLag() {
        long primaryLsn = walWriter.getLastLsn();
        Map<String, Long> lag = new ConcurrentHashMap<>();
        replicaAckedLsn.forEach((id, acked) -> lag.put(id, primaryLsn - acked));
        return lag;
    }

    private class WALReplicationServiceImpl
            extends WALReplicationServiceGrpc.WALReplicationServiceImplBase {

        @Override
        public void handshake(HandshakeRequest request,
                              StreamObserver<HandshakeResponse> responseObserver) {
            String replicaId  = request.getReplicaId();
            long   replicaLsn = request.getCurrentLsn();
            long   primaryLsn = walWriter.getLastLsn();

            log.info("Replica {} connected. replicaLsn={} lag={}", replicaId, replicaLsn, primaryLsn - replicaLsn);
            replicaQueues.put(replicaId, new LinkedBlockingQueue<>(50_000));
            replicaAckedLsn.put(replicaId, replicaLsn);

            responseObserver.onNext(HandshakeResponse.newBuilder()
                .setAccepted(true)
                .setPrimaryLsn(primaryLsn)
                .setCatchUpFrom(replicaLsn)
                .setMessage("Connected. Catch-up from LSN " + replicaLsn)
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void streamEntries(HandshakeRequest request,
                                  StreamObserver<ReplicationStream> responseObserver) {
            String replicaId  = request.getReplicaId();
            long   replicaLsn = request.getCurrentLsn();
            log.info("Starting stream for replica {} from LSN {}", replicaId, replicaLsn);

            try {
                // Phase 1: catch-up — send all missed entries
                List<WALEntry> missed = walReader.readFrom(replicaLsn + 1);
                log.info("Sending {} catch-up entries to replica {}", missed.size(), replicaId);
                for (WALEntry entry : missed) {
                    responseObserver.onNext(toStreamMessage(entry));
                }

                // Phase 2: live stream — block on queue, forward as entries arrive
                BlockingQueue<WALEntry> queue = replicaQueues.computeIfAbsent(
                    replicaId, id -> new LinkedBlockingQueue<>(50_000));

                while (!Thread.currentThread().isInterrupted()) {
                    WALEntry entry = queue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) responseObserver.onNext(toStreamMessage(entry));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Stream error for replica {}", replicaId, e);
                responseObserver.onError(e);
                return;
            } finally {
                replicaQueues.remove(replicaId);
            }
            responseObserver.onCompleted();
        }

        @Override
        public void acknowledge(AckRequest request,
                                StreamObserver<AckResponse> responseObserver) {
            replicaAckedLsn.merge(request.getReplicaId(), request.getAppliedLsn(), Math::max);
            responseObserver.onNext(AckResponse.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
        }

        @Override
        public void getReplicaStatus(ReplicaStatusRequest request,
                                     StreamObserver<ReplicaStatusResponse> responseObserver) {
            String  id        = request.getReplicaId();
            long    acked     = replicaAckedLsn.getOrDefault(id, 0L);
            long    lag       = walWriter.getLastLsn() - acked;
            boolean connected = replicaQueues.containsKey(id);

            responseObserver.onNext(ReplicaStatusResponse.newBuilder()
                .setReplicaId(id)
                .setAppliedLsn(acked)
                .setLag(lag)
                .setHealthy(connected && lag < 10_000)
                .setState(connected ? (lag > 0 ? "STREAMING" : "IN_SYNC") : "DISCONNECTED")
                .build());
            responseObserver.onCompleted();
        }

        private ReplicationStream toStreamMessage(WALEntry e) {
            return ReplicationStream.newBuilder()
                .setEntry(WALEntryProto.newBuilder()
                    .setLsn(e.getLsn())
                    .setTerm(e.getTerm())
                    .setEntryType(e.getType().code)
                    .setPayload(ByteString.copyFrom(e.getPayload()))
                    .setChecksum(e.getChecksum())
                    .setTimestamp(System.currentTimeMillis())
                    .build())
                .setPrimaryLsn(walWriter.getLastLsn())
                .build();
        }
    }
}
