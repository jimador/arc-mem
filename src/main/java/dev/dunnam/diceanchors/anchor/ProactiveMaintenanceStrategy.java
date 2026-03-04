package dev.dunnam.diceanchors.anchor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.assembly.CompactionValidator;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proactive (sleep-cycle) maintenance strategy executing a 5-step sweep: audit, refresh,
 * consolidate, prune, validate. Inspired by Sleeping LLM (Guo et al., 2025).
 *
 * <p>Sweep type is determined by memory pressure:
 * <ul>
 *   <li>pressure >= fullSweepThreshold (0.8): FULL sweep with optional LLM audit</li>
 *   <li>pressure >= lightSweepThreshold (0.4): LIGHT sweep with heuristic audit only</li>
 *   <li>pressure < lightSweepThreshold: no sweep</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Thread-safe. Per-context sweep state is held in a ConcurrentHashMap.
 *
 * <h2>Error contract</h2>
 * MUST NOT throw. On failure, logs the error and returns a safe result.
 */
public non-sealed class ProactiveMaintenanceStrategy implements MaintenanceStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ProactiveMaintenanceStrategy.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryPressureGauge pressureGauge;
    private final AnchorEngine anchorEngine;
    private final AnchorRepository repository;
    private final CanonizationGate canonizationGate;
    private final InvariantEvaluator invariantEvaluator;
    private final LlmCallService llmCallService;
    private final DiceAnchorsProperties.ProactiveConfig config;
    private final DiceAnchorsProperties.PressureConfig pressureConfig;
    private final int anchorBudget;
    private final @Nullable PrologAuditPreFilter prologPreFilter;

    private final Map<String, Integer> lastSweepTurn = new ConcurrentHashMap<>();

    public ProactiveMaintenanceStrategy(
            MemoryPressureGauge pressureGauge,
            AnchorEngine anchorEngine,
            AnchorRepository repository,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            LlmCallService llmCallService,
            DiceAnchorsProperties properties) {
        this(pressureGauge, anchorEngine, repository, canonizationGate, invariantEvaluator,
                llmCallService, properties, null);
    }

    public ProactiveMaintenanceStrategy(
            MemoryPressureGauge pressureGauge,
            AnchorEngine anchorEngine,
            AnchorRepository repository,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            LlmCallService llmCallService,
            DiceAnchorsProperties properties,
            @Nullable PrologAuditPreFilter prologPreFilter) {
        this.pressureGauge = pressureGauge;
        this.anchorEngine = anchorEngine;
        this.repository = repository;
        this.canonizationGate = canonizationGate;
        this.invariantEvaluator = invariantEvaluator;
        this.llmCallService = llmCallService;
        this.config = proactiveConfig(properties);
        this.pressureConfig = properties.pressure();
        this.anchorBudget = properties.anchor() != null ? properties.anchor().budget() : 20;
        this.prologPreFilter = prologPreFilter;
    }

    private static DiceAnchorsProperties.ProactiveConfig proactiveConfig(DiceAnchorsProperties properties) {
        if (properties.maintenance() != null && properties.maintenance().proactive() != null) {
            return properties.maintenance().proactive();
        }
        return new DiceAnchorsProperties.ProactiveConfig(
                10, 0.1, 0.3, 0.6, 10, 0.8, 5, 50, 50, false, false);
    }

    @Override
    public void onTurnComplete(MaintenanceContext context) {
        // Proactive strategy does not perform per-turn inline work.
    }

    @Override
    public boolean shouldRunSweep(MaintenanceContext context) {
        try {
            var pressure = pressureGauge.computePressure(
                    context.contextId(), context.activeAnchors().size(), anchorBudget);

            if (pressure == null) {
                return false;
            }

            double lightThreshold = pressureConfig != null
                    ? pressureConfig.lightSweepThreshold() : 0.4;
            if (pressure.total() < lightThreshold) {
                return false;
            }

            var metadata = context.metadata();
            boolean pressureOverride = metadata != null
                    && Boolean.TRUE.equals(metadata.get("pressureOverride"));
            if (pressureOverride) {
                return true;
            }

            int lastTurn = lastSweepTurn.getOrDefault(context.contextId(), -config.minTurnsBetweenSweeps());
            return (context.turnNumber() - lastTurn) >= config.minTurnsBetweenSweeps();
        } catch (Exception e) {
            logger.warn("shouldRunSweep failed for context={}: {}", context.contextId(), e.getMessage());
            return false;
        }
    }

    @Override
    public SweepResult executeSweep(MaintenanceContext context) {
        var start = Instant.now();
        var contextId = context.contextId();
        try {
            var pressure = pressureGauge.computePressure(
                    contextId, context.activeAnchors().size(), anchorBudget);
            var sweepType = determineSweepType(pressure);

            if (sweepType == SweepType.NONE) {
                return SweepResult.empty("Pressure below sweep threshold");
            }

            // Step 1: Audit
            List<AuditScore> auditScores;
            try {
                auditScores = auditAnchors(context, sweepType);
            } catch (Exception e) {
                logger.error("Audit step failed for context={}: {}", contextId, e.getMessage());
                lastSweepTurn.put(contextId, context.turnNumber());
                return SweepResult.empty("Sweep failed during audit: " + e.getMessage());
            }

            // Step 2: Refresh
            int refreshed = 0;
            try {
                refreshed = refreshAnchors(auditScores, context);
            } catch (Exception e) {
                logger.error("Refresh step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 3: Consolidate
            int consolidated = 0;
            try {
                consolidated = consolidateAnchors(auditScores, context);
            } catch (Exception e) {
                logger.error("Consolidate step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 4: Prune
            List<String> prunedIds = List.of();
            try {
                prunedIds = pruneAnchors(auditScores, context, pressure);
            } catch (Exception e) {
                logger.error("Prune step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 5: Validate
            final var effectivePrunedIds = prunedIds;
            int violations = 0;
            try {
                var remaining = context.activeAnchors().stream()
                        .filter(a -> !effectivePrunedIds.contains(a.id()))
                        .toList();
                violations = validateSweep(context, prunedIds, remaining);
            } catch (Exception e) {
                logger.error("Validate step failed for context={}: {}", contextId, e.getMessage());
            }

            lastSweepTurn.put(contextId, context.turnNumber());
            var duration = Duration.between(start, Instant.now());

            logger.info("Proactive sweep complete: type={} audited={} refreshed={} consolidated={} pruned={} violations={} duration={}ms",
                    sweepType, auditScores.size(), refreshed, consolidated, prunedIds.size(),
                    violations, duration.toMillis());

            var summary = String.format("Sweep[%s] audited=%d refreshed=%d consolidated=%d pruned=%d violations=%d",
                    sweepType, auditScores.size(), refreshed, consolidated, prunedIds.size(), violations);
            return new SweepResult(auditScores.size(), refreshed, prunedIds.size(),
                    auditScores.size() - prunedIds.size(), duration, summary);

        } catch (Exception e) {
            logger.error("Proactive sweep failed for context={}: {}", contextId, e.getMessage());
            lastSweepTurn.put(contextId, context.turnNumber());
            return SweepResult.empty("Sweep failed: " + e.getMessage());
        }
    }

    SweepType determineSweepType(PressureScore pressure) {
        if (pressureConfig == null) {
            return pressure.total() >= 0.8 ? SweepType.FULL
                    : pressure.total() >= 0.4 ? SweepType.LIGHT : SweepType.NONE;
        }
        if (pressure.total() >= pressureConfig.fullSweepThreshold()) {
            return SweepType.FULL;
        }
        if (pressure.total() >= pressureConfig.lightSweepThreshold()) {
            return SweepType.LIGHT;
        }
        return SweepType.NONE;
    }

    private List<AuditScore> auditAnchors(MaintenanceContext context, SweepType sweepType) {
        var anchors = context.activeAnchors();
        var scores = new ArrayList<AuditScore>(anchors.size());

        Set<String> prologFlagged = Set.of();
        if (config.prologPreFilterEnabled() && prologPreFilter != null) {
            prologFlagged = prologPreFilter.flagContradictingAnchors(anchors);
        }

        for (var anchor : anchors) {
            double heuristic = prologFlagged.contains(anchor.id())
                    ? 0.0
                    : computeHeuristicScore(anchor, context.turnNumber());
            scores.add(new AuditScore(anchor.id(), heuristic, heuristic, false));
        }

        if (sweepType == SweepType.FULL && config.llmAuditEnabled()) {
            scores = refineBorderlineWithLlm(scores, context);
        }

        return List.copyOf(scores);
    }

    private double computeHeuristicScore(Anchor anchor, int currentTurn) {
        // Recency: approximate turns since reinforcement using reinforcementCount as proxy
        // More reinforcements relative to turns = more recent engagement
        int maxTurns = config.minTurnsBetweenSweeps();
        double turnsSinceReinforcement = Math.max(0, currentTurn - anchor.reinforcementCount());
        double recency = 1.0 - Math.min(turnsSinceReinforcement / (double) maxTurns, 1.0);

        // Rank position: normalize to [0, 1]
        double rankPosition = (double) (anchor.rank() - Anchor.MIN_RANK)
                / (double) (Anchor.MAX_RANK - Anchor.MIN_RANK);

        // Memory tier: HOT=1.0, WARM=0.5, COLD=0.2
        double tierScore = switch (anchor.memoryTier()) {
            case HOT -> 1.0;
            case WARM -> 0.5;
            case COLD -> 0.2;
        };

        return (recency * 0.33) + (rankPosition * 0.33) + (tierScore * 0.34);
    }

    private ArrayList<AuditScore> refineBorderlineWithLlm(List<AuditScore> scores,
                                                           MaintenanceContext context) {
        var result = new ArrayList<>(scores);
        var borderline = scores.stream()
                .filter(s -> s.finalScore() > config.softPruneThreshold() && s.finalScore() < 0.7)
                .toList();

        if (borderline.isEmpty()) {
            return result;
        }

        var anchorMap = context.activeAnchors().stream()
                .collect(java.util.stream.Collectors.toMap(Anchor::id, a -> a));

        var sb = new StringBuilder("Rate the relevance of each anchor. Return a JSON array of objects with \"anchorId\" and \"score\" fields.\n\nAnchors:\n");
        for (var score : borderline) {
            var anchor = anchorMap.get(score.anchorId());
            if (anchor != null) {
                sb.append(String.format("- ID: %s, Text: \"%s\", Authority: %s, Rank: %d%n",
                        anchor.id(), anchor.text(), anchor.authority(), anchor.rank()));
            }
        }

        try {
            var systemPrompt = """
                    You are evaluating anchor relevance for a knowledge management system.
                    Rate each anchor's relevance on a scale of 0.0 to 1.0.
                    Return a JSON array of objects with "anchorId" and "score" fields only.""";
            var llmResponse = llmCallService.callBatched(systemPrompt, sb.toString());
            var llmScores = MAPPER.readValue(llmResponse, new TypeReference<List<LlmAuditEntry>>() {});

            for (var llmScore : llmScores) {
                for (int i = 0; i < result.size(); i++) {
                    if (result.get(i).anchorId().equals(llmScore.anchorId())) {
                        result.set(i, new AuditScore(llmScore.anchorId(),
                                result.get(i).heuristicScore(), llmScore.score(), true));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("LLM audit refinement failed for context={}, retaining heuristic scores: {}",
                    context.contextId(), e.getMessage());
        }

        return result;
    }

    private int refreshAnchors(List<AuditScore> auditScores, MaintenanceContext context) {
        var anchorMap = context.activeAnchors().stream()
                .collect(java.util.stream.Collectors.toMap(Anchor::id, a -> a));
        int refreshed = 0;

        for (var score : auditScores) {
            var anchor = anchorMap.get(score.anchorId());
            if (anchor == null) continue;

            if (score.finalScore() >= 0.7) {
                int newRank = Anchor.clampRank(anchor.rank() + config.rankBoostAmount());
                repository.updateRank(anchor.id(), newRank);
                logger.debug("Refreshed anchor {} rank {} -> {} (auditScore={} boost)",
                        anchor.id(), anchor.rank(), newRank, score.finalScore());
                refreshed++;
            } else if (score.finalScore() <= 0.3) {
                // CANON and pinned anchors are immune to rank penalty (invariants A3b, A3d)
                if (anchor.authority() == Authority.CANON || anchor.pinned()) {
                    continue;
                }
                int newRank = Anchor.clampRank(anchor.rank() - config.rankPenaltyAmount());
                repository.updateRank(anchor.id(), newRank);
                logger.debug("Refreshed anchor {} rank {} -> {} (auditScore={} penalty)",
                        anchor.id(), anchor.rank(), newRank, score.finalScore());
                refreshed++;
            }

            // Borderline zone: trigger trust re-evaluation
            if (score.finalScore() >= 0.2 && score.finalScore() <= 0.4) {
                try {
                    anchorEngine.reEvaluateTrust(anchor.id());
                } catch (Exception e) {
                    logger.warn("Trust re-evaluation failed for anchor {}: {}", anchor.id(), e.getMessage());
                }
            }
        }

        return refreshed;
    }

    private int consolidateAnchors(List<AuditScore> auditScores, MaintenanceContext context) {
        var anchorMap = context.activeAnchors().stream()
                .collect(java.util.stream.Collectors.toMap(Anchor::id, a -> a));
        int consolidated = 0;

        for (var score : auditScores) {
            var anchor = anchorMap.get(score.anchorId());
            if (anchor == null || anchor.authority() != Authority.RELIABLE) {
                continue;
            }

            // Candidacy criteria (all must be met):
            // 1. RELIABLE authority (already checked)
            // 2. reinforcementCount >= candidacyMinReinforcements
            // 3. auditScore >= candidacyMinAuditScore
            // 4. approximate age >= candidacyMinAge (reinforcementCount / 2 as proxy)
            boolean meetsReinforcements = anchor.reinforcementCount() >= config.candidacyMinReinforcements();
            boolean meetsAuditScore = score.finalScore() >= config.candidacyMinAuditScore();
            boolean meetsAge = (anchor.reinforcementCount() / 2) >= config.candidacyMinAge();

            if (!meetsReinforcements || !meetsAuditScore || !meetsAge) {
                continue;
            }

            logger.debug("Routing anchor {} to CanonizationGate (reinforcements={} score={} age~={})",
                    anchor.id(), anchor.reinforcementCount(), score.finalScore(),
                    anchor.reinforcementCount() / 2);

            canonizationGate.requestCanonization(
                    anchor.id(), context.contextId(), anchor.text(),
                    anchor.authority(), "proactive-maintenance candidacy criteria met",
                    "proactive-maintenance");
            consolidated++;
        }

        return consolidated;
    }

    private List<String> pruneAnchors(List<AuditScore> auditScores, MaintenanceContext context,
                                      PressureScore pressure) {
        var anchorMap = context.activeAnchors().stream()
                .collect(java.util.stream.Collectors.toMap(Anchor::id, a -> a));
        var pruned = new ArrayList<String>();

        for (var score : auditScores) {
            var anchor = anchorMap.get(score.anchorId());
            if (anchor == null) continue;

            // CANON and pinned anchors are immune to pruning (invariants A3b, A3d)
            if (anchor.authority() == Authority.CANON || anchor.pinned()) {
                continue;
            }

            boolean shouldPrune = false;
            if (score.finalScore() < config.hardPruneThreshold()) {
                shouldPrune = true;
            } else if (score.finalScore() < config.softPruneThreshold()
                    && pressure.total() >= config.softPrunePressureThreshold()) {
                shouldPrune = true;
            }

            if (shouldPrune) {
                logger.debug("Pruning anchor {} (score={} pressure={})",
                        anchor.id(), score.finalScore(), pressure.total());
                anchorEngine.archive(anchor.id(), ArchiveReason.PROACTIVE_MAINTENANCE);
                pruned.add(anchor.id());
            }
        }

        return List.copyOf(pruned);
    }

    private int validateSweep(MaintenanceContext context, List<String> prunedIds,
                               List<Anchor> remainingAnchors) {
        var contextId = context.contextId();
        int violations = 0;

        // Check invariants for each pruned anchor
        var prunedSet = new java.util.HashSet<>(prunedIds);
        for (var anchor : context.activeAnchors()) {
            if (!prunedSet.contains(anchor.id())) continue;

            var eval = invariantEvaluator.evaluate(contextId, ProposedAction.ARCHIVE,
                    remainingAnchors, anchor);
            if (eval.hasBlockingViolation() || eval.hasWarnings()) {
                for (var violation : eval.violations()) {
                    logger.warn("Post-sweep invariant violation for anchor {}: {} ({})",
                            anchor.id(), violation.constraintDescription(), violation.strength());
                    violations++;
                }
            }
        }

        // Check compaction integrity for protected anchors (CANON + pinned)
        var protectedAnchors = remainingAnchors.stream()
                .filter(a -> a.authority() == Authority.CANON || a.pinned())
                .toList();

        if (!protectedAnchors.isEmpty() && !remainingAnchors.isEmpty()) {
            var summary = remainingAnchors.stream()
                    .map(Anchor::text)
                    .collect(java.util.stream.Collectors.joining(" "));
            var losses = CompactionValidator.validate(summary, protectedAnchors, 0.5);
            for (var loss : losses) {
                logger.warn("Post-sweep compaction loss: protected anchor {} ({}) not adequately represented",
                        loss.anchorId(), loss.anchorText());
                violations++;
            }
        }

        return violations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmAuditEntry(String anchorId, double score) {}
}
