package com.walengine.config;

import com.walengine.core.WALEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class WALEngineConfig {

    @Value("${wal.directory:/tmp/wal-data}")
    private String walDirectory;

    @Value("${wal.segment.size-mb:64}")
    private long segmentSizeMb;

    @Value("${wal.role:PRIMARY}")
    private String role;

    @Value("${wal.grpc.port:9090}")
    private int grpcPort;

    @Value("${wal.primary.host:localhost}")
    private String primaryHost;

    @Value("${wal.primary.grpc-port:9090}")
    private int primaryGrpcPort;

    @Value("${wal.replica.id:replica-1}")
    private String replicaId;

    @Value("${wal.compaction.enabled:true}")
    private boolean compactionEnabled;

    @Bean
    public WALEngine walEngine() throws IOException {
        return WALEngine.builder()
            .walDirectory(Path.of(walDirectory))
            .segmentSizeBytes(segmentSizeMb * 1024 * 1024)
            .role(WALEngine.NodeRole.valueOf(role))
            .grpcPort(grpcPort)
            .primaryHost(primaryHost)
            .primaryGrpcPort(primaryGrpcPort)
            .replicaId(replicaId)
            .enableAutoCompaction(compactionEnabled)
            .build();
    }
}
