package com.walengine.api;

import com.walengine.core.WALEngine;
import com.walengine.model.WALEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Thin HTTP layer — all logic is in WALEngine.
 * Spring Boot used only to expose REST endpoints.
 */
@RestController
@RequestMapping("/api/wal")
public class WALController {

    private final WALEngine engine;

    public WALController(WALEngine engine) {
        this.engine = engine;
    }

    /** Append plain text — handy for curl testing */
    @PostMapping("/append/text")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> appendText(
            @RequestBody String text) {
        return engine.append(text.getBytes())
            .thenApply(lsn -> ResponseEntity.ok(Map.of(
                "lsn", lsn, "status", "committed", "bytes", text.length())));
    }

    /** Append base64-encoded binary payload */
    @PostMapping("/append")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> append(
            @RequestBody AppendRequest req) {
        byte[] payload = Base64.getDecoder().decode(req.payload());
        return engine.append(payload)
            .thenApply(lsn -> ResponseEntity.ok(Map.of(
                "lsn", lsn, "status", "committed", "bytes", payload.length)));
    }

    @PostMapping("/commit")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> commit() {
        return engine.commit()
            .thenApply(lsn -> ResponseEntity.ok(Map.of("lsn", lsn, "status", "committed")));
    }

    @PostMapping("/rollback")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> rollback() {
        return engine.rollback()
            .thenApply(lsn -> ResponseEntity.ok(Map.of("lsn", lsn, "status", "rolled_back")));
    }

    @PostMapping("/checkpoint")
    public ResponseEntity<Map<String, Object>> checkpoint() {
        try {
            long lsn = engine.checkpoint();
            return ResponseEntity.ok(Map.of("checkpointLsn", lsn, "status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/entries")
    public ResponseEntity<List<EntryDTO>> entries() {
        try {
            return ResponseEntity.ok(
                engine.readCommitted().stream()
                    .map(e -> new EntryDTO(
                        e.getLsn(), e.getTerm(), e.getType().name(),
                        new String(e.getPayload())))
                    .toList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "role",              engine.getRole().name(),
            "currentLsn",        engine.getCurrentLsn(),
            "segmentCount",      engine.getWriter().getSegments().size(),
            "activeSegmentBytes", engine.getWriter().getActiveSegment().getCurrentSize(),
            "status",            "healthy"
        ));
    }

    @PostMapping("/compact")
    public ResponseEntity<Map<String, Object>> compact(
            @RequestParam(defaultValue = "true") boolean archive) {
        try {
            int n = engine.compact(archive);
            return ResponseEntity.ok(Map.of("segmentsCompacted", n, "archived", archive));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/replication/lag")
    public ResponseEntity<Map<String, Long>> replicationLag() {
        return ResponseEntity.ok(engine.getReplicationLag());
    }

    public record AppendRequest(String payload) {}
    public record EntryDTO(long lsn, long term, String type, String payload) {}
}
