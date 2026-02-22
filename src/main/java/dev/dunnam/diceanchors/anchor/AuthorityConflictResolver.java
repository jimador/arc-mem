package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties.TierModifierConfig;
import io.opentelemetry.api.trace.Span;

/**
 * Authority-tiered conflict resolver implementing a graduated conflict matrix
 * with configurable thresholds and tier-aware resolution modifiers.
 * <p>
 * Resolution decisions are based on the existing anchor's authority level,
 * the incoming proposition's confidence score, and the existing anchor's
 * memory tier. Tier modifiers adjust the effective thresholds — HOT anchors
 * get a defensive bias (harder to replace/demote), COLD anchors get a
 * permissive bias (easier to replace/demote).
 *
 * <h2>Thread safety</h2>
 * This class is effectively immutable and therefore thread-safe.
 */
public class AuthorityConflictResolver implements ConflictResolver {

    private final double replaceThreshold;
    private final double demoteThreshold;
    private final TierModifierConfig tierModifiers;

    public AuthorityConflictResolver(double replaceThreshold, double demoteThreshold, TierModifierConfig tierModifiers) {
        this.replaceThreshold = replaceThreshold;
        this.demoteThreshold = demoteThreshold;
        this.tierModifiers = tierModifiers;
    }

    public AuthorityConflictResolver() {
        this(0.8, 0.6, null);
    }

    @Override
    public ConflictResolver.Resolution resolve(ConflictDetector.Conflict conflict) {
        var existingAuthority = conflict.existing().authority();
        var confidence = conflict.confidence();

        // CANON immunity — invariant A3b: never affected by tier modifiers
        if (existingAuthority == Authority.CANON) {
            setSpanAttributes(existingAuthority, confidence, conflict.existing().memoryTier(), Resolution.KEEP_EXISTING);
            return Resolution.KEEP_EXISTING;
        }

        var tierBias = tierBiasFor(conflict.existing().memoryTier());
        var effectiveReplace = replaceThreshold + tierBias;
        var effectiveDemote = demoteThreshold + tierBias;

        var resolution = switch (existingAuthority) {
            case CANON -> Resolution.KEEP_EXISTING; // unreachable, but needed for exhaustive switch
            case RELIABLE -> {
                if (confidence >= effectiveReplace) {
                    yield Resolution.REPLACE;
                } else if (confidence >= effectiveDemote) {
                    yield Resolution.DEMOTE_EXISTING;
                } else {
                    yield Resolution.KEEP_EXISTING;
                }
            }
            case UNRELIABLE -> {
                if (confidence >= effectiveDemote) {
                    yield Resolution.REPLACE;
                } else {
                    yield Resolution.DEMOTE_EXISTING;
                }
            }
            case PROVISIONAL -> Resolution.REPLACE;
        };

        // Task 4.2: OTEL span attributes for conflict resolution observability
        setSpanAttributes(existingAuthority, confidence, conflict.existing().memoryTier(), resolution);

        return resolution;
    }

    private void setSpanAttributes(Authority existingAuthority, double confidence,
                                    MemoryTier existingTier, Resolution resolution) {
        var span = Span.current();
        span.setAttribute("conflict.existing_authority", existingAuthority.name());
        span.setAttribute("conflict.incoming_confidence_band", confidenceBand(confidence));
        span.setAttribute("conflict.existing_tier", existingTier != null ? existingTier.name() : "UNKNOWN");
        span.setAttribute("conflict.resolution", resolution.name());
    }

    private double tierBiasFor(MemoryTier tier) {
        if (tierModifiers == null || tier == null) {
            return 0.0;
        }
        return switch (tier) {
            case HOT -> tierModifiers.hotDefenseModifier();
            case WARM -> tierModifiers.warmDefenseModifier();
            case COLD -> tierModifiers.coldDefenseModifier();
        };
    }

    /**
     * Returns the confidence band label for OTEL observation key-value pairs.
     */
    public static String confidenceBand(double confidence) {
        if (confidence < 0.4) return "LOW";
        if (confidence <= 0.8) return "MEDIUM";
        return "HIGH";
    }
}
