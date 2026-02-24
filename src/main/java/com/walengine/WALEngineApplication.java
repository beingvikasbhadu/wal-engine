package com.walengine;

import com.walengine.core.WALEngine;
import com.walengine.recovery.CrashRecovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WALEngineApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WALEngineApplication.class);

    @Autowired
    private WALEngine walEngine;

    @Value("${wal.role:PRIMARY}")
    private String role;

    public static void main(String[] args) {
        SpringApplication.run(WALEngineApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("═══════════════════════════════════════");
        log.info("  WAL Engine Starting — Role: {}", role);
        log.info("═══════════════════════════════════════");

        // Step 1: Always run crash recovery on startup
        CrashRecovery.RecoveryResult result = walEngine.recover(entry -> {
            // Application hook: apply recovered entries to in-memory state
            log.info("[RECOVERY] LSN={} type={} payload={}",
                entry.getLsn(), entry.getType(), new String(entry.getPayload()));
        });
        log.info("Recovery complete: {}", result);

        // Step 2: If replica, start receiving entries from primary
        if ("REPLICA".equalsIgnoreCase(role)) {
            walEngine.startReplication(entry -> {
                log.info("[REPLICATION] Received LSN={} type={}", entry.getLsn(), entry.getType());
            });
        }

        log.info("═══════════════════════════════════════");
        log.info("  WAL Engine Ready. LSN={}", walEngine.getCurrentLsn());
        log.info("  HTTP API:  http://localhost:8080/api/wal/status");
        log.info("  gRPC port: configured via wal.grpc.port");
        log.info("═══════════════════════════════════════");
    }
}
