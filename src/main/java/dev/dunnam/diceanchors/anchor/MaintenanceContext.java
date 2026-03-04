package dev.dunnam.diceanchors.anchor;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshot of the runtime context passed to {@link MaintenanceStrategy} at each decision point.
 *
 * <p>{@code contextId} scopes all anchor operations to a single conversation or simulation run —
 * the same ID used by {@code AnchorRepository} for tenant isolation.
 *
 * <p>{@code turnNumber} is 0-based; turn 0 is the scene-setting establishment turn (if any),
 * so stress-test turns begin at turn 1. Proactive strategies MAY use turnNumber to decide
 * sweep frequency (e.g., every N turns).
 *
 * <p>{@code metadata} is an open extension map for strategy-specific signals (e.g., drift
 * score, recent contradiction count, adversary pressure level). It is {@code @Nullable};
 * strategies MUST treat absence as equivalent to an empty map and MUST NOT mutate it.
 */
public record MaintenanceContext(
        String contextId,
        List<Anchor> activeAnchors,
        int turnNumber,
        @Nullable Map<String, Object> metadata
) {}
