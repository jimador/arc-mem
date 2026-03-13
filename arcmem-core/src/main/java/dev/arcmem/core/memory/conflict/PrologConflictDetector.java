package dev.arcmem.core.memory.conflict;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * LOGICAL conflict detection via Prolog (tuProlog/2p-kt via DICE 0.1.0-SNAPSHOT).
 *
 * <p>Implements deterministic pre-filter per Sleeping LLM (Guo et al., 2025): projects
 * units and incoming text to Prolog facts, queries contradiction rules, returns
 * {@link Conflict} records with full confidence (Prolog is deterministic).
 *
 * <p>Backs the {@link ConflictDetectionStrategy#LOGICAL} branch in
 * {@link CompositeConflictDetector}. Never throws -- returns empty list on any failure.
 *
 * <p>Uses ground queries (one per existing unit) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(PrologConflictDetector.class);

    private final MemoryUnitPrologProjector projector;

    public PrologConflictDetector(MemoryUnitPrologProjector projector) {
        this.projector = projector;
    }

    @Override
    public List<Conflict> detect(String incomingText, List<MemoryUnit> existingUnits) {
        if (existingUnits == null || existingUnits.isEmpty()) {
            return List.of();
        }

        var start = Instant.now();
        try {
            var engine = projector.projectWithIncoming(existingUnits, incomingText);
            var conflicts = new ArrayList<Conflict>();

            for (var unit : existingUnits) {
                var query = String.format("conflicts_with_incoming('%s')", escapeAtom(unit.id()));
                if (engine.query(query)) {
                    conflicts.add(new Conflict(unit, incomingText, 1.0,
                            "Prolog logical contradiction", DetectionQuality.FULL, ConflictType.CONTRADICTION));
                }
            }

            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            logger.info("Prolog conflict detection: incoming='{}...' units={} conflicts={} queryMs={}",
                    incomingText.substring(0, Math.min(60, incomingText.length())),
                    existingUnits.size(), conflicts.size(), ms);

            return List.copyOf(conflicts);

        } catch (Exception e) {
            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            logger.warn("Prolog conflict detection failed after {}ms, returning empty: {}", ms, e.getMessage());
            return List.of();
        }
    }

    private String escapeAtom(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
