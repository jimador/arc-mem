package dev.dunnam.diceanchors.anchor;

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
 * anchors and incoming text to Prolog facts, queries contradiction rules, returns
 * {@link Conflict} records with full confidence (Prolog is deterministic).
 *
 * <p>Backs the {@link ConflictDetectionStrategy#LOGICAL} branch in
 * {@link CompositeConflictDetector}. Never throws -- returns empty list on any failure.
 *
 * <p>Uses ground queries (one per existing anchor) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(PrologConflictDetector.class);

    private final AnchorPrologProjector projector;

    public PrologConflictDetector(AnchorPrologProjector projector) {
        this.projector = projector;
    }

    @Override
    public List<Conflict> detect(String incomingText, List<Anchor> existingAnchors) {
        if (existingAnchors == null || existingAnchors.isEmpty()) {
            return List.of();
        }

        var start = Instant.now();
        try {
            var engine = projector.projectWithIncoming(existingAnchors, incomingText);
            var conflicts = new ArrayList<Conflict>();

            for (var anchor : existingAnchors) {
                var query = String.format("conflicts_with_incoming('%s')", escapeAtom(anchor.id()));
                if (engine.query(query)) {
                    conflicts.add(new Conflict(anchor, incomingText, 1.0,
                            "Prolog logical contradiction", DetectionQuality.FULL, ConflictType.CONTRADICTION));
                }
            }

            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            logger.info("Prolog conflict detection: incoming='{}...' anchors={} conflicts={} queryMs={}",
                    incomingText.substring(0, Math.min(60, incomingText.length())),
                    existingAnchors.size(), conflicts.size(), ms);

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
