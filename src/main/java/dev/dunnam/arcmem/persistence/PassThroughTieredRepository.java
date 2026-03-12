package dev.dunnam.diceanchors.persistence;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;

import java.util.List;

/**
 * No-op tiered repository that delegates directly to {@link AnchorEngine} without caching
 * or tier filtering. Used as a fallback when {@code dice-anchors.tiered-storage.enabled}
 * is not set, so that consumers can depend on {@link TieredAnchorRepository} unconditionally.
 */
public class PassThroughTieredRepository extends TieredAnchorRepository {

    private final AnchorEngine passEngine;

    public PassThroughTieredRepository(AnchorEngine engine, DiceAnchorsProperties properties) {
        super(engine, null, properties);
        this.passEngine = engine;
    }

    @Override
    public List<Anchor> findActiveAnchorsForAssembly(String contextId) {
        return passEngine.inject(contextId);
    }

    @Override
    public List<Anchor> findAllTiersForContext(String contextId) {
        return passEngine.findByContext(contextId);
    }
}
