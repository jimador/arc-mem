package dev.dunnam.diceanchors.anchor;

import io.opentelemetry.api.trace.Span;

public class RevisionAwareConflictResolver implements ConflictResolver {

    private final AuthorityConflictResolver delegate;
    private final AnchorMutationStrategy mutationStrategy;
    private final boolean enabled;
    private final boolean reliableRevisable;
    private final double revisionConfidenceThreshold;
    private final double replaceThreshold;

    public RevisionAwareConflictResolver(
            AuthorityConflictResolver delegate,
            AnchorMutationStrategy mutationStrategy,
            boolean enabled,
            boolean reliableRevisable,
            double revisionConfidenceThreshold,
            double replaceThreshold) {
        this.delegate = delegate;
        this.mutationStrategy = mutationStrategy;
        this.enabled = enabled;
        this.reliableRevisable = reliableRevisable;
        this.revisionConfidenceThreshold = revisionConfidenceThreshold;
        this.replaceThreshold = replaceThreshold;
    }

    @Override
    public Resolution resolve(ConflictDetector.Conflict conflict) {
        var conflictType = normalize(conflict.conflictType());
        setConflictTypeAttributes(conflictType, conflict.reason());
        if (!enabled) {
            return delegateWithDecision(conflict, "delegated", "revision_disabled");
        }
        if (conflictType == ConflictType.WORLD_PROGRESSION) {
            return resolveDirectly(Resolution.COEXIST, "accepted", "world_progression");
        }
        if (conflictType == ConflictType.CONTRADICTION || conflict.existing() == null) {
            return delegateWithDecision(conflict, "delegated", "contradiction_path");
        }
        return resolveRevision(conflict);
    }

    private Resolution resolveRevision(ConflictDetector.Conflict conflict) {
        var probe = new MutationRequest(
                conflict.existing().id(), conflict.incomingText(),
                MutationSource.CONFLICT_RESOLVER, "conflict-resolver");
        if (mutationStrategy.evaluate(probe) instanceof MutationDecision.Deny) {
            return delegateWithDecision(conflict, "delegated", "mutation_strategy_denied");
        }
        var authority = conflict.existing().authority();
        var confidence = conflict.confidence();
        return switch (authority) {
            case CANON -> resolveDirectly(Resolution.KEEP_EXISTING, "rejected", "canon_immutable");
            case PROVISIONAL -> resolveDirectly(Resolution.REPLACE, "accepted", "provisional_revisable");
            case UNRELIABLE -> {
                if (confidence >= revisionConfidenceThreshold) {
                    yield resolveDirectly(Resolution.REPLACE, "accepted", "unreliable_above_threshold");
                }
                yield delegateWithDecision(conflict, "delegated", "unreliable_below_threshold");
            }
            case RELIABLE -> {
                if (!reliableRevisable) {
                    yield delegateWithDecision(conflict, "delegated", "reliable_locked");
                }
                if (confidence >= replaceThreshold) {
                    yield resolveDirectly(Resolution.REPLACE, "accepted", "reliable_above_replace_threshold");
                }
                yield delegateWithDecision(conflict, "delegated", "reliable_below_replace_threshold");
            }
        };
    }

    private Resolution delegateWithDecision(ConflictDetector.Conflict conflict, String decision, String reason) {
        setRevisionDecisionAttributes(decision, reason);
        return delegate.resolve(conflict);
    }

    private Resolution resolveDirectly(Resolution resolution, String decision, String reason) {
        setRevisionDecisionAttributes(decision, reason);
        Span.current().setAttribute("conflict.resolution", resolution.name());
        return resolution;
    }

    private void setConflictTypeAttributes(ConflictType conflictType, String reasoning) {
        var span = Span.current();
        span.setAttribute("conflict.type", conflictType.name());
        span.setAttribute("conflict.type.reasoning", reasoning != null ? reasoning : "");
    }

    private void setRevisionDecisionAttributes(String decision, String reason) {
        var span = Span.current();
        var eligible = "accepted".equals(decision);
        span.setAttribute("conflict.revision.eligible", eligible);
        span.setAttribute("conflict.revision.decision", decision);
        span.setAttribute("conflict.revision.reason", reason);
    }

    private ConflictType normalize(ConflictType conflictType) {
        return conflictType != null ? conflictType : ConflictType.CONTRADICTION;
    }
}
