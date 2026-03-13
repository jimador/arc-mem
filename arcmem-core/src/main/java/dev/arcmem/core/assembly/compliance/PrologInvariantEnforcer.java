package dev.arcmem.core.assembly.compliance;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Prolog-based compliance enforcer. Projects active units to Prolog facts and
 * queries contradiction rules to detect conflicts between the LLM response and
 * enforced units.
 *
 * <p>Implements deterministic pre-filter per Sleeping LLM (Guo et al., 2025):
 * logically decidable contradictions are caught without an extra LLM call.
 *
 * <p>Fail-open: returns {@link ComplianceResult#compliant(Duration)} on any Prolog failure.
 * Never throws. Thread-safe (stateless).
 *
 * <p>Uses ground queries (one per enforced unit) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologInvariantEnforcer implements ComplianceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PrologInvariantEnforcer.class);

    private final MemoryUnitPrologProjector projector;

    public PrologInvariantEnforcer(MemoryUnitPrologProjector projector) {
        this.projector = projector;
    }

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        var start = Instant.now();
        var policy = context.policy();

        var enforcedUnits = context.activeUnits().stream()
                .filter(a -> switch (a.authority()) {
                    case CANON -> policy.enforceCanon();
                    case RELIABLE -> policy.enforceReliable();
                    case UNRELIABLE -> policy.enforceUnreliable();
                    case PROVISIONAL -> policy.enforceProvisional();
                })
                .toList();

        if (enforcedUnits.isEmpty()) {
            return ComplianceResult.compliant(Duration.ZERO);
        }

        try {
            var engine = projector.projectWithIncoming(enforcedUnits, context.responseText());
            var violations = new ArrayList<ComplianceViolation>();

            for (var unit : enforcedUnits) {
                var query = String.format("conflicts_with_incoming('%s')", escapeAtom(unit.id()));
                if (engine.query(query)) {
                    violations.add(new ComplianceViolation(
                            unit.id(), unit.text(), unit.authority(),
                            "Prolog detected contradiction with response", 1.0));
                }
            }

            var action = determineAction(violations);
            var duration = Duration.between(start, Instant.now());

            logger.info("Prolog invariant enforcement: enforcedUnits={} violations={} action={} durationMs={}",
                    enforcedUnits.size(), violations.size(), action, duration.toMillis());

            return new ComplianceResult(violations.isEmpty(), List.copyOf(violations), action, duration);

        } catch (Exception e) {
            var duration = Duration.between(start, Instant.now());
            logger.warn("Prolog invariant enforcement failed after {}ms, returning compliant (fail-open): {}",
                    duration.toMillis(), e.getMessage());
            return ComplianceResult.compliant(duration);
        }
    }

    private ComplianceAction determineAction(List<ComplianceViolation> violations) {
        if (violations.isEmpty()) {
            return ComplianceAction.ACCEPT;
        }
        boolean hasHighAuthority = violations.stream()
                .anyMatch(v -> v.unitAuthority() == Authority.CANON
                        || v.unitAuthority() == Authority.RELIABLE);
        return hasHighAuthority ? ComplianceAction.REJECT : ComplianceAction.RETRY;
    }

    private String escapeAtom(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
