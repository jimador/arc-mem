package dev.dunnam.diceanchors.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prolog-based pre-filter for proactive maintenance audit.
 *
 * <p>Identifies anchors involved in logical contradictions before the heuristic scoring
 * loop, allowing contradicting anchors to be fast-pathed to score 0.0. Reduces LLM
 * audit batch size per Sleeping LLM (Guo et al., 2025).
 *
 * <p>Enabled via {@code dice-anchors.maintenance.proactive.prolog-pre-filter-enabled}.
 * Returns empty set on any Prolog failure (never throws).
 *
 * <p>Uses ground queries (one per anchor pair) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologAuditPreFilter {

    private static final Logger logger = LoggerFactory.getLogger(PrologAuditPreFilter.class);

    private final AnchorPrologProjector projector;

    public PrologAuditPreFilter(AnchorPrologProjector projector) {
        this.projector = projector;
    }

    /**
     * Returns the IDs of anchors involved in any logical contradiction pair.
     * Both sides of each contradiction pair are flagged.
     */
    public Set<String> flagContradictingAnchors(List<Anchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return Set.of();
        }

        var start = Instant.now();
        try {
            var engine = projector.project(anchors);
            var flagged = new HashSet<String>();
            int pairs = 0;

            for (int i = 0; i < anchors.size(); i++) {
                for (int j = i + 1; j < anchors.size(); j++) {
                    var a = anchors.get(i);
                    var b = anchors.get(j);
                    var query = String.format("contradicts('%s', '%s')",
                            escapeAtom(a.id()), escapeAtom(b.id()));
                    if (engine.query(query)) {
                        flagged.add(a.id());
                        flagged.add(b.id());
                        pairs++;
                    }
                }
            }

            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            logger.info("Prolog audit pre-filter: anchors={} contradictionPairs={} flagged={} queryMs={}",
                    anchors.size(), pairs, flagged.size(), ms);

            return Set.copyOf(flagged);

        } catch (Exception e) {
            long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
            logger.warn("Prolog audit pre-filter failed after {}ms, returning empty: {}", ms, e.getMessage());
            return Set.of();
        }
    }

    private String escapeAtom(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
