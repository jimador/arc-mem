package dev.arcmem.core.memory.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.assembly.compaction.CompactionValidator;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.canon.CanonizationGate;
import dev.arcmem.core.memory.canon.InvariantEvaluator;
import dev.arcmem.core.memory.canon.ProposedAction;
import dev.arcmem.core.memory.engine.ArcMemEngine;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.memory.trust.AuditScore;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository repository;
    private final CanonizationGate canonizationGate;
    private final InvariantEvaluator invariantEvaluator;
    private final LlmCallService llmCallService;
    private final ArcMemProperties.ProactiveConfig config;
    private final ArcMemProperties.PressureConfig pressureConfig;
    private final int unitBudget;

    private final Map<String, Integer> lastSweepTurn = new ConcurrentHashMap<>();

    public ProactiveMaintenanceStrategy(
            MemoryPressureGauge pressureGauge,
            ArcMemEngine arcMemEngine,
            MemoryUnitRepository repository,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            LlmCallService llmCallService,
            ArcMemProperties properties) {
        this.pressureGauge = pressureGauge;
        this.arcMemEngine = arcMemEngine;
        this.repository = repository;
        this.canonizationGate = canonizationGate;
        this.invariantEvaluator = invariantEvaluator;
        this.llmCallService = llmCallService;
        this.config = proactiveConfig(properties);
        this.pressureConfig = properties.pressure();
        this.unitBudget = properties.unit() != null ? properties.unit().budget() : 20;
    }

    private static ArcMemProperties.ProactiveConfig proactiveConfig(ArcMemProperties properties) {
        if (properties.maintenance() != null && properties.maintenance().proactive() != null) {
            return properties.maintenance().proactive();
        }
        return new ArcMemProperties.ProactiveConfig(
                10, 0.1, 0.3, 0.6, 10, 0.8, 5, 50, 50, false);
    }

    @Override
    public void onTurnComplete(MaintenanceContext context) {
        // Proactive strategy does not perform per-turn inline work.
    }

    @Override
    public boolean shouldRunSweep(MaintenanceContext context) {
        try {
            var pressure = pressureGauge.computePressure(
                    context.contextId(), context.activeUnits().size(), unitBudget);

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
                    contextId, context.activeUnits().size(), unitBudget);
            var sweepType = determineSweepType(pressure);

            if (sweepType == SweepType.NONE) {
                return SweepResult.empty("Pressure below sweep threshold");
            }

            // Step 1: Audit
            List<AuditScore> auditScores;
            try {
                auditScores = auditUnits(context, sweepType);
            } catch (Exception e) {
                logger.error("Audit step failed for context={}: {}", contextId, e.getMessage());
                lastSweepTurn.put(contextId, context.turnNumber());
                return SweepResult.empty("Sweep failed during audit: " + e.getMessage());
            }

            // Step 2: Refresh
            int refreshed = 0;
            try {
                refreshed = refreshUnits(auditScores, context);
            } catch (Exception e) {
                logger.error("Refresh step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 3: Consolidate
            int consolidated = 0;
            try {
                consolidated = consolidateUnits(auditScores, context);
            } catch (Exception e) {
                logger.error("Consolidate step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 4: Prune
            List<String> prunedIds = List.of();
            try {
                prunedIds = pruneUnits(auditScores, context, pressure);
            } catch (Exception e) {
                logger.error("Prune step failed for context={}: {}", contextId, e.getMessage());
            }

            // Step 5: Validate
            final var effectivePrunedIds = prunedIds;
            int violations = 0;
            try {
                var remaining = context.activeUnits().stream()
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

    private List<AuditScore> auditUnits(MaintenanceContext context, SweepType sweepType) {
        var units = context.activeUnits();
        var scores = new ArrayList<AuditScore>(units.size());

        for (var unit : units) {
            double heuristic = computeHeuristicScore(unit, context.turnNumber());
            scores.add(new AuditScore(unit.id(), heuristic, heuristic, false));
        }

        if (sweepType == SweepType.FULL && config.llmAuditEnabled()) {
            scores = refineBorderlineWithLlm(scores, context);
        }

        return List.copyOf(scores);
    }

    private double computeHeuristicScore(MemoryUnit unit, int currentTurn) {
        // Recency: approximate turns since reinforcement using reinforcementCount as proxy
        // More reinforcements relative to turns = more recent engagement
        int maxTurns = config.minTurnsBetweenSweeps();
        double turnsSinceReinforcement = Math.max(0, currentTurn - unit.reinforcementCount());
        double recency = 1.0 - Math.min(turnsSinceReinforcement / (double) maxTurns, 1.0);

        // Rank position: normalize to [0, 1]
        double rankPosition = (double) (unit.rank() - MemoryUnit.MIN_RANK)
                              / (double) (MemoryUnit.MAX_RANK - MemoryUnit.MIN_RANK);

        // Memory tier: HOT=1.0, WARM=0.5, COLD=0.2
        double tierScore = switch (unit.memoryTier()) {
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

        var unitMap = context.activeUnits().stream()
                             .collect(Collectors.toMap(MemoryUnit::id, a -> a));

        var sb = new StringBuilder("Rate the relevance of each unit. Return a JSON array of objects with \"unitId\" and \"score\" fields.\n\nUnits:\n");
        for (var score : borderline) {
            var unit = unitMap.get(score.unitId());
            if (unit != null) {
                sb.append(String.format("- ID: %s, Text: \"%s\", Authority: %s, Rank: %d%n",
                                        unit.id(), unit.text(), unit.authority(), unit.rank()));
            }
        }

        try {
            var systemPrompt = """
                    You are evaluating unit relevance for a knowledge management system.
                    Rate each unit's relevance on a scale of 0.0 to 1.0.
                    Return a JSON array of objects with "unitId" and "score" fields only.""";
            var llmResponse = llmCallService.callBatched(systemPrompt, sb.toString());
            var llmScores = MAPPER.readValue(llmResponse, new TypeReference<List<LlmAuditEntry>>() {});

            for (var llmScore : llmScores) {
                for (int i = 0; i < result.size(); i++) {
                    if (result.get(i).unitId().equals(llmScore.unitId())) {
                        result.set(i, new AuditScore(llmScore.unitId(),
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

    private int refreshUnits(List<AuditScore> auditScores, MaintenanceContext context) {
        var unitMap = context.activeUnits().stream()
                             .collect(Collectors.toMap(MemoryUnit::id, a -> a));
        int refreshed = 0;

        for (var score : auditScores) {
            var unit = unitMap.get(score.unitId());
            if (unit == null) {
                continue;
            }

            if (score.finalScore() >= 0.7) {
                int newRank = MemoryUnit.clampRank(unit.rank() + config.rankBoostAmount());
                repository.updateRank(unit.id(), newRank);
                logger.debug("Refreshed unit {} rank {} -> {} (auditScore={} boost)",
                             unit.id(), unit.rank(), newRank, score.finalScore());
                refreshed++;
            } else if (score.finalScore() <= 0.3) {
                // CANON and pinned units are immune to rank penalty (invariants A3b, A3d)
                if (unit.authority() == Authority.CANON || unit.pinned()) {
                    continue;
                }
                int newRank = MemoryUnit.clampRank(unit.rank() - config.rankPenaltyAmount());
                repository.updateRank(unit.id(), newRank);
                logger.debug("Refreshed unit {} rank {} -> {} (auditScore={} penalty)",
                             unit.id(), unit.rank(), newRank, score.finalScore());
                refreshed++;
            }

            // Borderline zone: trigger trust re-evaluation
            if (score.finalScore() >= 0.2 && score.finalScore() <= 0.4) {
                try {
                    arcMemEngine.reEvaluateTrust(unit.id());
                } catch (Exception e) {
                    logger.warn("Trust re-evaluation failed for unit {}: {}", unit.id(), e.getMessage());
                }
            }
        }

        return refreshed;
    }

    private int consolidateUnits(List<AuditScore> auditScores, MaintenanceContext context) {
        var unitMap = context.activeUnits().stream()
                             .collect(Collectors.toMap(MemoryUnit::id, a -> a));
        int consolidated = 0;

        for (var score : auditScores) {
            var unit = unitMap.get(score.unitId());
            if (unit == null || unit.authority() != Authority.RELIABLE) {
                continue;
            }

            // Candidacy criteria (all must be met):
            // 1. RELIABLE authority (already checked)
            // 2. reinforcementCount >= candidacyMinReinforcements
            // 3. auditScore >= candidacyMinAuditScore
            // 4. approximate age >= candidacyMinAge (reinforcementCount / 2 as proxy)
            boolean meetsReinforcements = unit.reinforcementCount() >= config.candidacyMinReinforcements();
            boolean meetsAuditScore = score.finalScore() >= config.candidacyMinAuditScore();
            boolean meetsAge = (unit.reinforcementCount() / 2) >= config.candidacyMinAge();

            if (!meetsReinforcements || !meetsAuditScore || !meetsAge) {
                continue;
            }

            logger.debug("Routing unit {} to CanonizationGate (reinforcements={} score={} age~={})",
                         unit.id(), unit.reinforcementCount(), score.finalScore(),
                         unit.reinforcementCount() / 2);

            canonizationGate.requestCanonization(
                    unit.id(), context.contextId(), unit.text(),
                    unit.authority(), "proactive-maintenance candidacy criteria met",
                    "proactive-maintenance");
            consolidated++;
        }

        return consolidated;
    }

    private List<String> pruneUnits(List<AuditScore> auditScores, MaintenanceContext context,
                                    PressureScore pressure) {
        var unitMap = context.activeUnits().stream()
                             .collect(Collectors.toMap(MemoryUnit::id, a -> a));
        var pruned = new ArrayList<String>();

        for (var score : auditScores) {
            var unit = unitMap.get(score.unitId());
            if (unit == null) {
                continue;
            }

            // CANON and pinned units are immune to pruning (invariants A3b, A3d)
            if (unit.authority() == Authority.CANON || unit.pinned()) {
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
                logger.debug("Pruning unit {} (score={} pressure={})",
                             unit.id(), score.finalScore(), pressure.total());
                arcMemEngine.archive(unit.id(), ArchiveReason.PROACTIVE_MAINTENANCE);
                pruned.add(unit.id());
            }
        }

        return List.copyOf(pruned);
    }

    private int validateSweep(MaintenanceContext context, List<String> prunedIds,
                              List<MemoryUnit> remainingUnits) {
        var contextId = context.contextId();
        int violations = 0;

        // Check invariants for each pruned unit
        var prunedSet = new HashSet<>(prunedIds);
        for (var unit : context.activeUnits()) {
            if (!prunedSet.contains(unit.id())) {
                continue;
            }

            var eval = invariantEvaluator.evaluate(contextId, ProposedAction.ARCHIVE,
                                                   remainingUnits, unit);
            if (eval.hasBlockingViolation() || eval.hasWarnings()) {
                for (var violation : eval.violations()) {
                    logger.warn("Post-sweep invariant violation for unit {}: {} ({})",
                                unit.id(), violation.constraintDescription(), violation.strength());
                    violations++;
                }
            }
        }

        // Check compaction integrity for protected units (CANON + pinned)
        var protectedUnits = remainingUnits.stream()
                                           .filter(a -> a.authority() == Authority.CANON || a.pinned())
                                           .toList();

        if (!protectedUnits.isEmpty() && !remainingUnits.isEmpty()) {
            var summary = remainingUnits.stream()
                                        .map(MemoryUnit::text)
                                        .collect(Collectors.joining(" "));
            var losses = CompactionValidator.validate(summary, protectedUnits, 0.5);
            for (var loss : losses) {
                logger.warn("Post-sweep compaction loss: protected unit {} ({}) not adequately represented",
                            loss.unitId(), loss.unitText());
                violations++;
            }
        }

        return violations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmAuditEntry(String unitId, double score) {}
}
