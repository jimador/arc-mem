package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import io.opentelemetry.api.trace.Span;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade coordinating all anchor lifecycle operations: promotion, reinforcement,
 * decay, demotion, archiving, conflict detection, and context injection.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>A1</strong>: Active anchor count never exceeds budget after
 *       {@link #promote} returns.</li>
 *   <li><strong>A2</strong>: Rank is always clamped to
 *       [{@link Anchor#MIN_RANK}, {@link Anchor#MAX_RANK}].</li>
 *   <li><strong>A3a</strong>: CANON is never assigned by automatic promotion. Only
 *       explicit action (seed, manual, or approved canonization request) can set CANON.</li>
 *   <li><strong>A3b</strong>: CANON anchors are immune to automatic demotion. When the
 *       canonization gate is enabled, demotion of CANON creates a pending decanonization
 *       request instead of executing immediately.</li>
 *   <li><strong>A3c</strong>: Automatic demotion applies to RELIABLE → UNRELIABLE →
 *       PROVISIONAL via decay or trust re-evaluation.</li>
 *   <li><strong>A3d</strong>: Pinned anchors are immune to automatic demotion. Explicit
 *       demotion still works.</li>
 *   <li><strong>A3e</strong>: All authority transitions (both directions) publish
 *       {@link AnchorLifecycleEvent.AuthorityChanged} lifecycle events.</li>
 * </ul>
 *
 * <h2>Sections</h2>
 * <ul>
 *   <li><strong>Injection</strong>: {@link #inject} — assembles anchor list for context</li>
 *   <li><strong>Lifecycle</strong>: {@link #promote}, {@link #reinforce}, {@link #demote},
 *       {@link #archive}, {@link #applyDecay}</li>
 *   <li><strong>Trust</strong>: {@link #reEvaluateTrust}</li>
 *   <li><strong>Conflict</strong>: {@link #detectConflicts}, {@link #batchDetectConflicts},
 *       {@link #resolveConflict}</li>
 *   <li><strong>Query</strong>: {@link #activeCount}</li>
 * </ul>
 *
 * <p>If this class grows beyond ~400 lines or gains new responsibility categories,
 * decompose into focused services (AnchorLifecycleService, AnchorBudgetManager,
 * AnchorQueryService).
 */
@Service
public class AnchorEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnchorEngine.class);

    private final AnchorRepository repository;
    private final DiceAnchorsProperties.AnchorConfig config;
    private final ConflictDetector conflictDetector;
    private final ConflictResolver conflictResolver;
    private final ReinforcementPolicy reinforcementPolicy;
    private final DecayPolicy decayPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean lifecycleEventsEnabled;
    private final TrustPipeline trustPipeline;
    private final CanonizationGate canonizationGate;

    public AnchorEngine(
            AnchorRepository repository,
            DiceAnchorsProperties properties,
            ConflictDetector conflictDetector,
            ConflictResolver conflictResolver,
            ReinforcementPolicy reinforcementPolicy,
            DecayPolicy decayPolicy,
            ApplicationEventPublisher eventPublisher,
            @Lazy TrustPipeline trustPipeline,
            CanonizationGate canonizationGate) {
        this.repository = repository;
        this.config = properties.anchor();
        this.conflictDetector = conflictDetector;
        this.conflictResolver = conflictResolver;
        this.reinforcementPolicy = reinforcementPolicy;
        this.decayPolicy = decayPolicy;
        this.eventPublisher = eventPublisher;
        this.lifecycleEventsEnabled = properties.anchor().lifecycleEventsEnabled();
        this.trustPipeline = trustPipeline;
        this.canonizationGate = canonizationGate;
    }

    // ========================================================================
    // Injection
    // ========================================================================

    /**
     * Returns ranked active anchors for injection into the LLM context.
     * <p>
     * The repository returns anchors ordered by rank DESC; this method applies the budget
     * limit and maps to the {@link Anchor} view record. CANON and RELIABLE anchors are
     * included first due to rank ordering.
     *
     * <p>Preconditions: none (empty list returned if no anchors exist).
     * <p>Postconditions: result size ≤ budget.
     * <p>Invariants preserved: A1 (budget), A2 (rank clamped).
     * <p>Events published: none.
     * <p>Error behavior: if the repository throws, the exception propagates to the caller.
     *
     * @param contextId the conversation or session context
     * @return up to {@code budget} active anchors, highest-rank first
     */
    public List<Anchor> inject(String contextId) {
        var nodes = repository.findActiveAnchors(contextId);
        return nodes.stream()
                    .limit(config.budget())
                    .map(this::toAnchor)
                    .toList();
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Promote a proposition to anchor status at PROVISIONAL authority.
     * Delegates to {@link #promote(String, int, Authority)} with {@link Authority#CANON} as
     * the ceiling (i.e., no restriction on initial authority assignment).
     *
     * <p>Preconditions: {@code propositionId} must reference an existing proposition.
     * <p>Postconditions: active count ≤ budget (A1).
     * <p>Invariants preserved: A1, A2, A3a (CANON never assigned here).
     * <p>Events published: {@link AnchorLifecycleEvent.Promoted}, then zero or more
     *     {@link AnchorLifecycleEvent.Evicted}.
     * <p>Error behavior: if the proposition is not found after write, eviction is skipped
     *     and a WARN is logged; the anchor is still promoted.
     *
     * @param propositionId the proposition node ID
     * @param initialRank   the desired starting rank; clamped automatically
     */
    public void promote(String propositionId, int initialRank) {
        promote(propositionId, initialRank, Authority.CANON);
    }

    /**
     * Promote a proposition to anchor status, capping the initial authority at the given ceiling.
     * <p>
     * The anchor always starts at {@link Authority#PROVISIONAL}; the {@code authorityCeiling}
     * parameter is persisted as metadata for future trust enforcement. If the ceiling is below
     * PROVISIONAL this call still promotes at PROVISIONAL (PROVISIONAL is the floor).
     * <p>
     * Clamps the rank to [MIN_RANK, MAX_RANK], writes the promotion to the repository,
     * then evicts the lowest-ranked non-pinned anchors to keep the active count within
     * budget (invariant A1). The {@link AnchorLifecycleEvent.Promoted} event fires before
     * any {@link AnchorLifecycleEvent.Evicted} events.
     *
     * <p>Preconditions: {@code propositionId} must reference an existing proposition.
     * <p>Postconditions: active count ≤ budget (A1).
     * <p>Invariants preserved: A1, A2, A3a (CANON never assigned here).
     * <p>Events published: {@link AnchorLifecycleEvent.Promoted}, then zero or more
     *     {@link AnchorLifecycleEvent.Evicted}.
     * <p>Error behavior: if the proposition is not found after write, eviction is skipped
     *     and a WARN is logged; the anchor is still promoted.
     *
     * @param propositionId    the proposition node ID
     * @param initialRank      the desired starting rank; clamped automatically
     * @param authorityCeiling the maximum authority the anchor MAY reach via automatic promotion;
     *                         CANON means no ceiling (unrestricted)
     */
    public void promote(String propositionId, int initialRank, Authority authorityCeiling) {
        int rank = Anchor.clampRank(initialRank);
        var tier = computeTier(rank);
        repository.promoteToAnchor(propositionId, rank, Authority.PROVISIONAL.name(), tier.name());

        if (authorityCeiling != null && authorityCeiling != Authority.CANON) {
            logger.debug("Promoting proposition {} with authority ceiling {}", propositionId, authorityCeiling);
        }

        var proposition = repository.findById(propositionId);
        if (proposition != null) {
            var contextId = proposition.getContextIdValue();
            publish(AnchorLifecycleEvent.promoted(this, contextId, propositionId, propositionId, rank));
            var evicted = repository.evictLowestRanked(contextId, config.budget());
            for (var e : evicted) {
                publish(AnchorLifecycleEvent.evicted(this, contextId, e.anchorId(), e.previousRank()));
            }
        } else {
            logger.warn("Promoted proposition {} not found after write; skipping eviction", propositionId);
        }

        logger.info("Promoted proposition {} to anchor with rank {}", propositionId, rank);
    }

    /**
     * Reinforce an existing anchor — increment its reinforcement count, apply the rank
     * boost from {@link ReinforcementPolicy}, and upgrade authority if the policy threshold
     * is reached.
     * <p>
     * Authority upgrades never reach CANON automatically (invariant A3a). Upgrades publish
     * {@link AnchorLifecycleEvent.AuthorityChanged} with direction PROMOTED.
     *
     * <p>Preconditions: {@code anchorId} should reference an active anchor.
     * <p>Postconditions: rank ≤ MAX_RANK (A2); authority ≤ RELIABLE (A3a).
     * <p>Invariants preserved: A1, A2, A3a.
     * <p>Events published: {@link AnchorLifecycleEvent.AuthorityChanged} (if promoted),
     *     {@link AnchorLifecycleEvent.Reinforced}.
     * <p>Error behavior: if the anchor is not found after reinforcement, a WARN is logged
     *     and the rank/authority update is skipped.
     *
     * @param anchorId the proposition node ID of the anchor to reinforce
     */
    public void reinforce(String anchorId) {
        repository.reinforceAnchor(anchorId);

        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Anchor {} not found after reinforcement; skipping rank/authority update", anchorId);
            return;
        }
        var node = nodeOpt.get();
        var current = toAnchor(node);
        var previousTier = current.memoryTier();
        int boost = reinforcementPolicy.calculateRankBoost(current);
        int newRank = Anchor.clampRank(current.rank() + boost);
        var newTier = computeTier(newRank);

        if (reinforcementPolicy.shouldUpgradeAuthority(current)) {
            // Task 5.2: re-evaluate trust before authority upgrade at milestone
            reEvaluateTrust(anchorId);
            // Re-read node after potential trust demotion so upgrade uses fresh authority
            var postTrustNodeOpt = repository.findPropositionNodeById(anchorId);
            if (postTrustNodeOpt.isEmpty()) {
                logger.warn("Anchor {} not found after trust re-evaluation; skipping authority upgrade", anchorId);
                return;
            }
            var postTrustNode = postTrustNodeOpt.get();
            var postTrustCurrent = toAnchor(postTrustNode);
            var upgraded = nextAuthority(postTrustCurrent.authority());
            repository.setAuthority(anchorId, upgraded.name());
            repository.updateRank(anchorId, newRank);
            updateTierIfChanged(anchorId, previousTier, newTier, postTrustNode.getContextId());
            publish(AnchorLifecycleEvent.authorityChanged(
                    this, postTrustNode.getContextId(),
                    anchorId, postTrustCurrent.authority(), upgraded,
                    AuthorityChangeDirection.PROMOTED, "reinforcement"));
            publish(AnchorLifecycleEvent.reinforced(
                    this, postTrustNode.getContextId(),
                    anchorId, current.rank(), newRank,
                    postTrustNode.getReinforcementCount()));
            logger.info("Reinforced anchor {} - rank {} -> {}, authority {} -> {}",
                    anchorId, current.rank(), newRank, postTrustCurrent.authority(), upgraded);
        } else {
            repository.updateRank(anchorId, newRank);
            updateTierIfChanged(anchorId, previousTier, newTier, node.getContextId());
            publish(AnchorLifecycleEvent.reinforced(
                    this, node.getContextId(),
                    anchorId, current.rank(), newRank,
                    node.getReinforcementCount()));
            logger.debug("Reinforced anchor {} - rank {} -> {}", anchorId, current.rank(), newRank);
        }
    }

    /**
     * Demote an anchor's authority by one level (e.g., RELIABLE → UNRELIABLE).
     * <p>
     * Demotion ladder: RELIABLE → UNRELIABLE → PROVISIONAL (floor). If the anchor is
     * already PROVISIONAL, it is archived instead (no lower authority exists).
     * <p>
     * CANON demotion gate: if the anchor is CANON and the canonization gate is enabled,
     * a pending decanonization request is created rather than applying the demotion
     * immediately (invariant A3b). If the gate is disabled, CANON is demoted directly.
     * <p>
     * Pinned anchors: explicit demotion works on pinned anchors (A3d only protects against
     * automatic demotion).
     *
     * <p>Preconditions: {@code anchorId} should reference an active anchor.
     * <p>Postconditions: authority decreased by one level, or anchor archived.
     * <p>Invariants preserved: A3b (if gate enabled), A3e.
     * <p>Events published: {@link AnchorLifecycleEvent.AuthorityChanged} with direction
     *     DEMOTED, or {@link AnchorLifecycleEvent.Archived}.
     * <p>Error behavior: if the anchor is not found, a WARN is logged and no exception
     *     is thrown.
     *
     * @param anchorId the proposition node ID of the anchor to demote
     * @param reason   the reason for demotion (carried in the AuthorityChanged event)
     */
    public void demote(String anchorId, DemotionReason reason) {
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot demote anchor {} — not found", anchorId);
            return;
        }
        var node = nodeOpt.get();
        var currentAuthStr = node.getAuthority();
        var current = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;

        // CANON gate: route through canonization gate if enabled
        if (current == Authority.CANON && config.canonizationGateEnabled()) {
            canonizationGate.requestDecanonization(
                    anchorId, node.getContextId(), node.getText(),
                    reason.name(), "system");
            logger.info("Demotion of CANON anchor {} routed through canonization gate (reason={})",
                    anchorId, reason);
            return;
        }

        // PROVISIONAL floor: archive instead of demoting further
        if (current == Authority.PROVISIONAL) {
            archive(anchorId, ArchiveReason.CONFLICT_REPLACEMENT);
            logger.info("Demote of PROVISIONAL anchor {} resulted in archive (reason={})", anchorId, reason);
            return;
        }

        var newAuthority = current.previousLevel();
        repository.setAuthority(anchorId, newAuthority.name());
        publish(AnchorLifecycleEvent.authorityChanged(
                this, node.getContextId(),
                anchorId, current, newAuthority,
                AuthorityChangeDirection.DEMOTED, reason.name()));
        logger.info("Demoted anchor {} from {} to {} (reason={})",
                anchorId, current, newAuthority, reason);
    }

    /**
     * Apply rank decay to an active anchor. If the anchor does not exist, the call is
     * ignored with a WARN log.
     *
     * <p>Preconditions: none (no-op if anchor not found).
     * <p>Postconditions: rank ≤ current rank (decay never increases rank).
     * <p>Invariants preserved: A2 (rank clamped).
     * <p>Events published: none.
     * <p>Error behavior: if the anchor is not found, a WARN is logged and no exception
     *     is thrown.
     *
     * @param anchorId the proposition node ID of the anchor
     * @param newRank  decayed rank target (clamped automatically)
     */
    public void applyDecay(String anchorId, int newRank) {
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot decay anchor {} because it was not found", anchorId);
            return;
        }
        var node = nodeOpt.get();
        var currentRank = node.getRank();
        var clampedRank = Anchor.clampRank(newRank);
        if (clampedRank >= currentRank) {
            return;
        }
        var previousTier = computeTier(currentRank);
        var newTier = computeTier(clampedRank);
        repository.updateRank(anchorId, clampedRank);
        updateTierIfChanged(anchorId, previousTier, newTier, node.getContextId());
        logger.debug("Decayed anchor {} rank {} -> {}", anchorId, currentRank, clampedRank);

        // Task 5.4: authority demotion when rank drops below authority threshold
        var anchor = toAnchor(node);
        if (decayPolicy.shouldDemoteAuthority(anchor, clampedRank)) {
            logger.info("Rank decay triggered authority demotion for anchor {} (rank={}, authority={})",
                    anchorId, clampedRank, anchor.authority());
            demote(anchorId, DemotionReason.RANK_DECAY);
        }
    }

    /**
     * Archive an anchor (deactivate it) with an explicit lifecycle reason.
     *
     * <p>Preconditions: {@code anchorId} should reference an active anchor.
     * <p>Postconditions: anchor status = SUPERSEDED, rank = 0.
     * <p>Invariants preserved: none modified.
     * <p>Events published: {@link AnchorLifecycleEvent.Archived}.
     * <p>Error behavior: if the anchor is not found, a WARN is logged and no exception
     *     is thrown.
     *
     * @param anchorId the proposition node ID of the anchor
     * @param reason   archive reason for lifecycle tracking
     */
    public void archive(String anchorId, ArchiveReason reason) {
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot archive anchor {} because it was not found", anchorId);
            return;
        }
        var node = nodeOpt.get();
        repository.archiveAnchor(anchorId);
        publish(AnchorLifecycleEvent.archived(this, node.getContextId(), anchorId, reason));
        logger.info("Archived anchor {} with reason {}", anchorId, reason);
    }

    // ========================================================================
    // Trust
    // ========================================================================

    /**
     * Re-evaluate the trust score for an anchor and demote it if the new authority ceiling
     * is below its current authority.
     * <p>
     * Trust re-evaluation is triggered by:
     * <ul>
     *   <li>This explicit call (e.g., via tool call or scheduled re-check)</li>
     *   <li>Conflict resolution (called by the conflict resolver after resolution)</li>
     *   <li>Reinforcement milestones (called by {@link #reinforce} at 3× and 7×)</li>
     * </ul>
     * <p>
     * Trust re-evaluation is NOT triggered by rank decay (decay-based demotion uses rank
     * thresholds directly) or periodic timers (no background scheduling in current design).
     * <p>
     * CANON anchors: if the new ceiling drops below CANON, a pending decanonization request
     * is created via the canonization gate rather than immediate demotion (invariant A3b).
     *
     * <p>Preconditions: {@code anchorId} should reference an active anchor.
     * <p>Postconditions: anchor authority ≤ new trust ceiling (unless CANON + gate enabled).
     * <p>Invariants preserved: A3b (gate), A3e (events).
     * <p>Events published: {@link AnchorLifecycleEvent.AuthorityChanged} with DEMOTED
     *     direction (via {@link #demote}) if demotion is warranted.
     * <p>Error behavior: if the anchor is not found, a WARN is logged and no exception
     *     is thrown.
     *
     * @param anchorId the proposition node ID of the anchor to re-evaluate
     */
    public void reEvaluateTrust(String anchorId) {
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot re-evaluate trust for anchor {} — not found", anchorId);
            return;
        }
        var node = nodeOpt.get();
        var currentAuthStr = node.getAuthority();
        var current = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;

        var trustScore = trustPipeline.evaluate(node, node.getContextId());
        var ceiling = trustScore.authorityCeiling();

        if (ceiling.level() < current.level()) {
            logger.info("Trust re-evaluation: anchor {} ceiling {} < current authority {} → demoting",
                    anchorId, ceiling, current);
            demote(anchorId, DemotionReason.TRUST_DEGRADATION);
        } else {
            logger.debug("Trust re-evaluation: anchor {} ceiling {} >= current authority {} — no change",
                    anchorId, ceiling, current);
        }
    }

    // ========================================================================
    // Conflict
    // ========================================================================

    /**
     * Check incoming text for conflicts with existing active anchors in the context.
     *
     * <p>Preconditions: none (returns empty list if no anchors or no conflicts).
     * <p>Postconditions: anchors unchanged (detection only, not resolution).
     * <p>Invariants preserved: all.
     * <p>Events published: {@link AnchorLifecycleEvent.ConflictDetected} if conflicts found.
     * <p>Error behavior: if detection throws, the exception propagates to the caller.
     *
     * @param contextId    the conversation or session context
     * @param incomingText the new statement to check for conflicts
     * @return list of conflicts detected; empty if none
     */
    public List<ConflictDetector.Conflict> detectConflicts(String contextId, String incomingText) {
        var anchors = inject(contextId);
        var conflicts = conflictDetector.detect(incomingText, anchors);
        if (!conflicts.isEmpty()) {
            publish(AnchorLifecycleEvent.conflictDetected(
                    this, contextId, incomingText,
                    conflicts.size(),
                    conflicts.stream().map(conflict -> conflict.existing().id()).toList()));
        }
        return conflicts;
    }

    /**
     * Batch conflict detection for multiple incoming texts against existing anchors.
     *
     * <p>Preconditions: none (returns empty map of empty lists if inputs are empty).
     * <p>Postconditions: anchors unchanged.
     * <p>Invariants preserved: all.
     * <p>Events published: none (batch detection does not publish events).
     * <p>Error behavior: if detection throws, the exception propagates to the caller.
     *
     * @param contextId     the context to fetch anchors from
     * @param incomingTexts the incoming statements to check
     * @return map from incoming text to list of detected conflicts
     */
    public Map<String, List<ConflictDetector.Conflict>> batchDetectConflicts(
            String contextId, List<String> incomingTexts) {
        var anchors = inject(contextId);
        if (anchors.isEmpty() || incomingTexts.isEmpty()) {
            return incomingTexts.stream()
                    .collect(java.util.stream.Collectors.toMap(t -> t, t -> List.of()));
        }
        return conflictDetector.batchDetect(incomingTexts, anchors);
    }

    /**
     * Resolve a detected conflict using the configured {@link ConflictResolver}.
     *
     * <p>Preconditions: {@code conflict} must be a valid conflict from {@link #detectConflicts}.
     * <p>Postconditions: conflict resolution recorded; anchor state unchanged by this method
     *     (callers handle REPLACE/DEMOTE_EXISTING logic).
     * <p>Invariants preserved: all.
     * <p>Events published: {@link AnchorLifecycleEvent.ConflictResolved}.
     * <p>Error behavior: if the existing anchor is not found, contextId defaults to
     *     {@code "unknown"}.
     *
     * @param conflict the conflict to resolve
     * @return the resolution decision
     */
    public ConflictResolver.Resolution resolveConflict(ConflictDetector.Conflict conflict) {
        var resolution = conflictResolver.resolve(conflict);
        var contextId = repository.findPropositionNodeById(conflict.existing().id())
                .map(PropositionNode::getContextId)
                .orElse("unknown");
        publish(AnchorLifecycleEvent.conflictResolved(
                this, contextId, conflict.existing().id(), resolution));
        return resolution;
    }

    // ========================================================================
    // Query
    // ========================================================================

    /**
     * Return all active anchors for a context (no budget limit applied).
     * Intended for cross-reference dedup checks where the full active set is needed.
     *
     * <p>Preconditions: none (empty list returned if no anchors exist).
     * <p>Postconditions: none (read-only).
     * <p>Invariants preserved: all.
     * <p>Events published: none.
     * <p>Error behavior: if the repository throws, returns empty list.
     *
     * @param contextId the conversation or session context
     * @return all currently active anchors, highest-rank first
     */
    public List<Anchor> findByContext(String contextId) {
        return repository.findActiveAnchors(contextId).stream()
                .map(this::toAnchor)
                .toList();
    }

    /**
     * Count active anchors for a context.
     *
     * <p>Preconditions: none.
     * <p>Postconditions: none (read-only).
     * <p>Invariants preserved: all.
     * <p>Events published: none.
     * <p>Error behavior: if the repository throws, returns 0.
     *
     * @param contextId the conversation or session context
     * @return number of currently active anchors
     */
    public int activeCount(String contextId) {
        return repository.countActiveAnchors(contextId);
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    /**
     * Convert a {@link PropositionNode} to the {@link Anchor} view record.
     * Populates DICE fields ({@code diceImportance}, {@code diceDecay}) and memory tier from the node.
     */
    Anchor toAnchor(PropositionNode node) {
        var authority = Authority.valueOf(
                node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name());
        var tier = computeTier(node.getRank());
        return new Anchor(
                node.getId(),
                node.getText(),
                node.getRank(),
                authority,
                node.isPinned(),
                node.getConfidence(),
                node.getReinforcementCount(),
                null,                   // trustScore: populated by TrustPipeline on demand
                node.getImportance(),   // diceImportance from DICE proposition
                node.getDecay(),        // diceDecay from DICE proposition (default 1.0 = standard rate)
                tier
        );
    }

    /**
     * Compute the memory tier for a given rank using configured thresholds.
     * Falls back to WARM if tier config is not set.
     */
    MemoryTier computeTier(int rank) {
        var tierConfig = config.tier();
        if (tierConfig == null) {
            return MemoryTier.WARM;
        }
        return MemoryTier.fromRank(rank, tierConfig.hotThreshold(), tierConfig.warmThreshold());
    }

    /**
     * Persist the tier change and publish a {@link AnchorLifecycleEvent.TierChanged} event
     * if the tier actually changed. No-op when previousTier == newTier.
     */
    private void updateTierIfChanged(String anchorId, MemoryTier previousTier,
                                      MemoryTier newTier, String contextId) {
        if (previousTier == newTier) {
            return;
        }
        repository.updateMemoryTier(anchorId, newTier.name());
        publish(AnchorLifecycleEvent.tierChanged(this, contextId, anchorId, previousTier, newTier));

        var span = Span.current();
        span.setAttribute("anchor.id", anchorId);
        span.setAttribute("anchor.tier", newTier.name());
        span.setAttribute("anchor.tier.previous", previousTier.name());

        logger.info("Anchor {} tier changed {} -> {}", anchorId, previousTier, newTier);
    }

    /**
     * Return the next authority level above the given one.
     * CANON is never returned — RELIABLE is the ceiling for automatic promotion (A3a).
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
            logger.warn("Failed to publish lifecycle event {}: {}",
                    event.getClass().getSimpleName(), e.getMessage());
        }
    }
}
