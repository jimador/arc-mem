package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.anchor.event.AnchorArchivedEvent;
import dev.dunnam.diceanchors.anchor.event.AnchorPromotedEvent;
import dev.dunnam.diceanchors.anchor.event.AnchorReinforcedEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.anchor.event.AuthorityUpgradedEvent;
import dev.dunnam.diceanchors.anchor.event.ConflictDetectedEvent;
import dev.dunnam.diceanchors.anchor.event.ConflictResolvedEvent;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates anchor budget enforcement, rank ordering, conflict detection,
 * promotion, and reinforcement.
 * <p>
 * Invariant A1: active anchor count never exceeds budget after promote() returns.
 * Invariant A2: rank is always clamped to [Anchor.MIN_RANK, Anchor.MAX_RANK].
 * Invariant A3: CANON authority is never assigned automatically.
 */
@Service
public class AnchorEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnchorEngine.class);

    private final AnchorRepository repository;
    private final DiceAnchorsProperties.AnchorConfig config;
    private final ConflictDetector conflictDetector;
    private final ConflictResolver conflictResolver;
    private final ReinforcementPolicy reinforcementPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean lifecycleEventsEnabled;

    public AnchorEngine(
            AnchorRepository repository,
            DiceAnchorsProperties properties,
            ConflictDetector conflictDetector,
            ConflictResolver conflictResolver,
            ReinforcementPolicy reinforcementPolicy,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.config = properties.anchor();
        this.conflictDetector = conflictDetector;
        this.conflictResolver = conflictResolver;
        this.reinforcementPolicy = reinforcementPolicy;
        this.eventPublisher = eventPublisher;
        this.lifecycleEventsEnabled = properties.anchor().lifecycleEventsEnabled();
    }

    /**
     * Return ranked active anchors within budget for context injection.
     * The repository returns anchors ordered by rank DESC; this method
     * applies the budget limit and maps to the Anchor view record.
     *
     * @param contextId the conversation or session context
     *
     * @return up to {@code budget} active anchors, highest-rank first
     */
    public List<Anchor> inject(String contextId) {
        var nodes = repository.findActiveAnchors(contextId);
        return nodes.stream()
                    .limit(config.budget())
                    .map(this::toAnchor)
                    .toList();
    }

    /**
     * Promote a proposition to anchor status.
     * Clamps the rank to [MIN_RANK, MAX_RANK], writes the promotion to the
     * repository, then evicts the lowest-ranked non-pinned anchors to keep
     * the active count within budget (invariant A1).
     *
     * @param propositionId the proposition node ID
     * @param initialRank   the desired starting rank; clamped automatically
     */
    public void promote(String propositionId, int initialRank) {
        int rank = Anchor.clampRank(initialRank);
        repository.promoteToAnchor(propositionId, rank, Authority.PROVISIONAL.name());

        var proposition = repository.findById(propositionId);
        if (proposition != null) {
            var contextId = proposition.getContextIdValue();
            repository.evictLowestRanked(contextId, config.budget());
            publish(new AnchorPromotedEvent(this, contextId, propositionId, propositionId, rank));
        } else {
            logger.warn("Promoted proposition {} not found after write; skipping eviction", propositionId);
        }

        logger.info("Promoted proposition {} to anchor with rank {}", propositionId, rank);
    }

    /**
     * Reinforce an existing anchor - increment its reinforcement count,
     * apply the rank boost from {@link ReinforcementPolicy}, and upgrade
     * authority if the policy threshold is reached.
     *
     * @param anchorId the proposition node ID of the anchor to reinforce
     */
    public void reinforce(String anchorId) {
        repository.reinforceAnchor(anchorId);

        var node = repository.findPropositionNodeById(anchorId);
        if (node == null) {
            logger.warn("Anchor {} not found after reinforcement; skipping rank/authority update", anchorId);
            return;
        }

        var current = toAnchor(node);
        int boost = reinforcementPolicy.calculateRankBoost(current);
        int newRank = Anchor.clampRank(current.rank() + boost);

        if (reinforcementPolicy.shouldUpgradeAuthority(current)) {
            var upgraded = nextAuthority(current.authority());
            repository.upgradeAuthority(anchorId, upgraded.name());
            repository.updateRank(anchorId, newRank);
            publish(new AuthorityUpgradedEvent(
                    this,
                    node.getContextId(),
                    anchorId,
                    current.authority(),
                    upgraded,
                    node.getReinforcementCount()));
            publish(new AnchorReinforcedEvent(
                    this,
                    node.getContextId(),
                    anchorId,
                    current.rank(),
                    newRank,
                    node.getReinforcementCount()));
            logger.info("Reinforced anchor {} - rank {} -> {}, authority {} -> {}",
                    anchorId, current.rank(), newRank, current.authority(), upgraded);
        } else {
            repository.updateRank(anchorId, newRank);
            publish(new AnchorReinforcedEvent(
                    this,
                    node.getContextId(),
                    anchorId,
                    current.rank(),
                    newRank,
                    node.getReinforcementCount()));
            logger.debug("Reinforced anchor {} - rank {} -> {}", anchorId, current.rank(), newRank);
        }
    }

    /**
     * Apply rank decay to an active anchor.
     * If the anchor does not exist, the call is ignored.
     *
     * @param anchorId the proposition node ID of the anchor
     * @param newRank  decayed rank target (clamped automatically)
     */
    public void applyDecay(String anchorId, int newRank) {
        var node = repository.findPropositionNodeById(anchorId);
        if (node == null) {
            logger.warn("Cannot decay anchor {} because it was not found", anchorId);
            return;
        }
        var currentRank = node.getRank();
        var clampedRank = Anchor.clampRank(newRank);
        if (clampedRank >= currentRank) {
            return;
        }
        repository.updateRank(anchorId, clampedRank);
        logger.debug("Decayed anchor {} rank {} -> {}", anchorId, currentRank, clampedRank);
    }

    /**
     * Archive an anchor (deactivate it) with an explicit lifecycle reason.
     *
     * @param anchorId the proposition node ID of the anchor
     * @param reason   archive reason for lifecycle tracking
     */
    public void archive(String anchorId, ArchiveReason reason) {
        var node = repository.findPropositionNodeById(anchorId);
        if (node == null) {
            logger.warn("Cannot archive anchor {} because it was not found", anchorId);
            return;
        }
        repository.archiveAnchor(anchorId);
        publish(new AnchorArchivedEvent(
                this,
                node.getContextId(),
                anchorId,
                reason));
        logger.info("Archived anchor {} with reason {}", anchorId, reason);
    }

    /**
     * Check incoming text for conflicts with existing active anchors in the context.
     *
     * @param contextId    the conversation or session context
     * @param incomingText the new statement to check for conflicts
     *
     * @return list of conflicts detected; empty if none
     */
    public List<ConflictDetector.Conflict> detectConflicts(String contextId, String incomingText) {
        var anchors = inject(contextId);
        var conflicts = conflictDetector.detect(incomingText, anchors);
        if (!conflicts.isEmpty()) {
            publish(new ConflictDetectedEvent(
                    this,
                    contextId,
                    incomingText,
                    conflicts.size(),
                    conflicts.stream().map(conflict -> conflict.existing().id()).toList()));
        }
        return conflicts;
    }

    /**
     * Resolve a detected conflict using the configured {@link ConflictResolver}.
     *
     * @param conflict the conflict to resolve
     *
     * @return the resolution decision
     */
    public ConflictResolver.Resolution resolveConflict(ConflictDetector.Conflict conflict) {
        var resolution = conflictResolver.resolve(conflict);
        var existingNode = repository.findPropositionNodeById(conflict.existing().id());
        var contextId = existingNode != null ? existingNode.getContextId() : "unknown";
        publish(new ConflictResolvedEvent(
                this,
                contextId,
                conflict.existing().id(),
                resolution));
        return resolution;
    }

    /**
     * Count active anchors for a context.
     *
     * @param contextId the conversation or session context
     *
     * @return number of currently active anchors
     */
    public int activeCount(String contextId) {
        return repository.countActiveAnchors(contextId);
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    Anchor toAnchor(PropositionNode node) {
        return Anchor.withoutTrust(
                node.getId(),
                node.getText(),
                node.getRank(),
                Authority.valueOf(node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name()),
                node.isPinned(),
                node.getConfidence(),
                node.getReinforcementCount()
        );
    }

    /**
     * Return the next authority level above the given one.
     * CANON is never returned - RELIABLE is the ceiling for automatic promotion
     * (invariant A3).
     */
    private Authority nextAuthority(Authority current) {
        return switch (current) {
            case PROVISIONAL -> Authority.UNRELIABLE;
            case UNRELIABLE -> Authority.RELIABLE;
            case RELIABLE, CANON -> Authority.RELIABLE;
        };
    }

    private void publish(AnchorLifecycleEvent event) {
        if (!lifecycleEventsEnabled) {
            return;
        }
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish lifecycle event {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }
}
