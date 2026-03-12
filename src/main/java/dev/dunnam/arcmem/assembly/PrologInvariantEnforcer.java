package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.AnchorPrologProjector;
import dev.dunnam.diceanchors.anchor.Authority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Prolog-based compliance enforcer. Projects active anchors to Prolog facts and
 * queries contradiction rules to detect conflicts between the LLM response and
 * enforced anchors.
 *
 * <p>Implements deterministic pre-filter per Sleeping LLM (Guo et al., 2025):
 * logically decidable contradictions are caught without an extra LLM call.
 *
 * <p>Fail-open: returns {@link ComplianceResult#compliant(Duration)} on any Prolog failure.
 * Never throws. Thread-safe (stateless).
 *
 * <p>Uses ground queries (one per enforced anchor) to avoid relying on variable binding
 * extraction from tuProlog substitutions.
 */
@Service
public class PrologInvariantEnforcer implements ComplianceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PrologInvariantEnforcer.class);

    private final AnchorPrologProjector projector;

    public PrologInvariantEnforcer(AnchorPrologProjector projector) {
        this.projector = projector;
    }

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        var start = Instant.now();
        var policy = context.policy();

        var enforcedAnchors = context.activeAnchors().stream()
                .filter(a -> switch (a.authority()) {
                    case CANON -> policy.enforceCanon();
                    case RELIABLE -> policy.enforceReliable();
                    case UNRELIABLE -> policy.enforceUnreliable();
                    case PROVISIONAL -> policy.enforceProvisional();
                })
                .toList();

        if (enforcedAnchors.isEmpty()) {
            return ComplianceResult.compliant(Duration.ZERO);
        }

        try {
            var engine = projector.projectWithIncoming(enforcedAnchors, context.responseText());
            var violations = new ArrayList<ComplianceViolation>();

            for (var anchor : enforcedAnchors) {
                var query = String.format("conflicts_with_incoming('%s')", escapeAtom(anchor.id()));
                if (engine.query(query)) {
                    violations.add(new ComplianceViolation(
                            anchor.id(), anchor.text(), anchor.authority(),
                            "Prolog detected contradiction with response", 1.0));
                }
            }

            var action = determineAction(violations);
            var duration = Duration.between(start, Instant.now());

            logger.info("Prolog invariant enforcement: enforcedAnchors={} violations={} action={} durationMs={}",
                    enforcedAnchors.size(), violations.size(), action, duration.toMillis());

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
                .anyMatch(v -> v.anchorAuthority() == Authority.CANON
                        || v.anchorAuthority() == Authority.RELIABLE);
        return hasHighAuthority ? ComplianceAction.REJECT : ComplianceAction.RETRY;
    }

    private String escapeAtom(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
