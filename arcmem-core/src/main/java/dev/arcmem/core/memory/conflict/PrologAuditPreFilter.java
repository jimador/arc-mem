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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prolog-based pre-filter for proactive maintenance audit.
 *
 * <p>Identifies units involved in logical contradictions before the heuristic scoring
 * loop, allowing contradicting units to be fast-pathed to score 0.0. Reduces LLM
 * audit batch size per Sleeping LLM (Guo et al., 2025).
 *
 * <p>Enabled via {@code arc-mem.maintenance.proactive.prolog-pre-filter-enabled}.
 * Returns empty set on any Prolog failure (never throws).
 *
 * <p>Uses ground queries (one per unit pair) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologAuditPreFilter {

    private static final Logger logger = LoggerFactory.getLogger(PrologAuditPreFilter.class);

    private final MemoryUnitPrologProjector projector;

    public PrologAuditPreFilter(MemoryUnitPrologProjector projector) {
        this.projector = projector;
    }

    /**
     * Returns the IDs of units involved in any logical contradiction pair.
     * Both sides of each contradiction pair are flagged.
     */
    public Set<String> flagContradictingUnits(List<MemoryUnit> units) {
        if (units == null || units.isEmpty()) {
            return Set.of();
        }

        var start = Instant.now();
        try {
            var engine = projector.project(units);
            var flagged = new HashSet<String>();
            int pairs = 0;

            for (int i = 0; i < units.size(); i++) {
                for (int j = i + 1; j < units.size(); j++) {
                    var a = units.get(i);
                    var b = units.get(j);
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
            logger.info("Prolog audit pre-filter: units={} contradictionPairs={} flagged={} queryMs={}",
                    units.size(), pairs, flagged.size(), ms);

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
