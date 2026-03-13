package dev.arcmem.simulator.engine;
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
import dev.arcmem.simulator.compaction.CompactedContextProvider;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.config.ArcMemSimulatorProperties;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import dev.arcmem.simulator.prompt.SimulationPromptPaths;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Executes a single simulation turn: player message → unit context assembly
 * → DM response generation → optional drift evaluation.
 * <p>
 * Drift evaluation is performed only on adversarial ATTACK turns where ground
 * truth is provided. Warm-up and establish turns are not evaluated.
 * <p>
 * When {@code parallelPostResponse} is enabled, drift evaluation (Branch A) and
 * proposition extraction (Branch B) are forked concurrently after the DM response
 * using {@link StructuredTaskScope} with a {@code awaitAllSuccessfulOrThrow} joiner.
 * If either branch fails the sibling is cancelled and the exception is propagated.
 * When the flag is false, the existing sequential execution path is used unchanged.
 */
@Component
public class SimulationTurnExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SimulationTurnExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    // 5-minute ceiling for the combined post-response parallel block; long enough
    // for real LLM evaluation + DICE extraction pipeline calls.
    private static final Duration PARALLEL_BRANCH_TIMEOUT = Duration.ofMinutes(5);

    private final ChatModelHolder chatModel;
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository contextUnitRepository;
    private final ArcMemProperties properties;
    private final ArcMemSimulatorProperties simulatorProperties;
    private final CompliancePolicy compliancePolicy;
    private final TokenCounter tokenCounter;
    private final SimulationExtractionService extractionService;
    private final RelevanceScorer relevanceScorer;
    private final ArcMemProperties.UnitConfig unitConfig;
    private final String driftEvalSystemPrompt;
    private final SimulationTurnServices turnServices;

    public SimulationTurnExecutor(
            ChatModelHolder chatModel,
            ArcMemEngine arcMemEngine,
            MemoryUnitRepository contextUnitRepository,
            ArcMemProperties properties,
            ArcMemSimulatorProperties simulatorProperties,
            CompliancePolicy compliancePolicy,
            TokenCounter tokenCounter,
            RelevanceScorer relevanceScorer,
            SimulationTurnServices turnServices) {
        this.chatModel = chatModel;
        this.arcMemEngine = arcMemEngine;
        this.contextUnitRepository = contextUnitRepository;
        this.properties = properties;
        this.simulatorProperties = simulatorProperties;
        this.compliancePolicy = compliancePolicy;
        this.tokenCounter = tokenCounter;
        this.turnServices = turnServices;
        this.extractionService = turnServices.extractionService();
        this.relevanceScorer = relevanceScorer;
        this.unitConfig = properties != null ? properties.unit() : null;
        this.driftEvalSystemPrompt = PromptTemplates.load(PromptPathConstants.DRIFT_EVALUATION_SYSTEM);
    }

    /**
     * Execute one full turn of the simulation.
     *
     * @param attackStrategies attack techniques when turnType is ATTACK; empty list if none
     * @param groundTruth      facts to evaluate against; may be null or empty
     */
    @Observed(name = "simulation.turn")
    public SimulationTurn executeTurn(
            String contextId,
            int turnNumber,
            String playerMessage,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String setting,
            boolean injectionEnabled,
            int tokenBudget,
            List<SimulationScenario.GroundTruth> groundTruth,
            List<String> conversationHistory) {

        var currentSpan = Span.current();
        currentSpan.setAttribute("sim.turn", turnNumber);
        currentSpan.setAttribute("sim.turn_type", turnType.name());
        if (attackStrategies != null && !attackStrategies.isEmpty()) {
            currentSpan.setAttribute("sim.strategy",
                                     attackStrategies.stream().map(Enum::name).collect(Collectors.joining(",")));
        }

        var unitRef = new ArcMemLlmReference(
                arcMemEngine,
                contextId,
                properties.unit().budget(),
                compliancePolicy,
                tokenBudget,
                tokenCounter,
                null,
                properties.retrieval(),
                relevanceScorer);
        var propositionRef = new PropositionsLlmReference(
                contextUnitRepository,
                contextId,
                properties.unit().budget());
        List<MemoryUnit> units = unitRef.getUnits();

        var hotCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.HOT).count();
        var warmCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.WARM).count();
        var coldCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.COLD).count();
        currentSpan.setAttribute("unit.tier.hot_count", hotCount);
        currentSpan.setAttribute("unit.tier.warm_count", warmCount);
        currentSpan.setAttribute("unit.tier.cold_count", coldCount);

        var unitBlock = injectionEnabled ? unitRef.getContent() : "";
        var propositionBlock = injectionEnabled ? propositionRef.getContent() : "";
        var injectedContextBlock = combineInjectedBlocks(unitBlock, propositionBlock);

        var systemPrompt = buildSystemPrompt(setting, unitBlock, propositionBlock);
        var userPrompt = buildUserPrompt(playerMessage, conversationHistory);

        int injectionPatternsDetected = scanForInjectionPatterns(playerMessage);

        var dmResponse = callLlm(systemPrompt, userPrompt);

        var complianceSnapshot = enforceCompliance(dmResponse, units, injectionEnabled);

        // token estimates: ~4 chars per token
        var unitTokens = injectedContextBlock.length() / 4;
        var totalTokens = (systemPrompt.length() + userPrompt.length() + dmResponse.length()) / 4;
        var trace = new ContextTrace(
                turnNumber, unitTokens, totalTokens,
                units, injectionEnabled, injectedContextBlock,
                systemPrompt, userPrompt,
                tokenBudget > 0,
                unitRef.getLastBudgetResult().excluded().size(),
                0, 0, 0, List.of(),
                hotCount, warmCount, coldCount,
                complianceSnapshot, injectionPatternsDetected, SweepSnapshot.none());

        var verdicts = shouldEvaluate(turnType, groundTruth)
                ? evaluateDrift(dmResponse, groundTruth, playerMessage, units)
                : List.<EvalVerdict> of();

        logger.info("Turn {} [{}]: player='{}', dm='{}', units={}, verdicts={}",
                    turnNumber, turnType,
                    truncate(playerMessage, 50), truncate(dmResponse, 50),
                    units.size(), verdicts.size());

        return new SimulationTurn(
                turnNumber, playerMessage, dmResponse,
                turnType, attackStrategies, trace, verdicts, List.of());
    }

    /**
     * Execute a full turn with unit state diffing and optional compaction.
     * Returns a {@link TurnExecutionResult} containing the turn, current unit state,
     * and any compaction result.
     * <p>
     * When {@code parallelPostResponse} is true, drift evaluation (Branch A) and
     * proposition extraction + promotion (Branch B) run concurrently after the DM
     * response using {@link StructuredTaskScope}. Sequential operations (reinforce,
     * dormancy, state diff, compaction) follow the join point.
     * <p>
     * When {@code parallelPostResponse} is false, the original sequential execution
     * path is preserved for debugging and test reproducibility.
     *
     * @param attackStrategies    attack techniques when turnType is ATTACK; empty list if none
     * @param groundTruth         facts to evaluate against; may be null or empty
     * @param previousUnitState unit state from end of previous turn (keyed by id)
     * @param compactionProvider  null to skip compaction
     * @param compactionConfig    null to skip compaction
     * @param dormancyConfig      null disables decay/archive
     * @param dormancyState       mutable per-unit dormancy counters carried across turns
     */
    public TurnExecutionResult executeTurnFull(
            String contextId,
            int turnNumber,
            String playerMessage,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String setting,
            boolean injectionEnabled,
            int tokenBudget,
            List<SimulationScenario.GroundTruth> groundTruth,
            List<String> conversationHistory,
            Map<String, MemoryUnit> previousUnitState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {
        return executeTurnFull(
                contextId,
                turnNumber,
                playerMessage,
                turnType,
                attackStrategies,
                setting,
                injectionEnabled,
                tokenBudget,
                groundTruth,
                conversationHistory,
                previousUnitState,
                compactionProvider,
                compactionConfig,
                extractionEnabled,
                dormancyConfig,
                dormancyState,
                true,
                true);
    }

    public TurnExecutionResult executeTurnFull(
            String contextId,
            int turnNumber,
            String playerMessage,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String setting,
            boolean injectionEnabled,
            int tokenBudget,
            List<SimulationScenario.GroundTruth> groundTruth,
            List<String> conversationHistory,
            Map<String, MemoryUnit> previousUnitState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState,
            boolean rankMutationEnabled,
            boolean authorityPromotionEnabled) {
        if (simulatorProperties.sim() != null && simulatorProperties.sim().parallelPostResponse()) {
            return executeTurnFullParallel(
                    contextId, turnNumber, playerMessage, turnType, attackStrategies,
                    setting, injectionEnabled, tokenBudget, groundTruth, conversationHistory,
                    previousUnitState, compactionProvider, compactionConfig,
                    extractionEnabled, dormancyConfig, dormancyState,
                    rankMutationEnabled, authorityPromotionEnabled);
        }
        return executeTurnFullSequential(
                contextId, turnNumber, playerMessage, turnType, attackStrategies,
                setting, injectionEnabled, tokenBudget, groundTruth, conversationHistory,
                previousUnitState, compactionProvider, compactionConfig,
                extractionEnabled, dormancyConfig, dormancyState,
                rankMutationEnabled, authorityPromotionEnabled);
    }

    /**
     * Parallel execution: DM response is generated first (sequential), then
     * Branch A (drift evaluation) and Branch B (extraction + promotion) run
     * concurrently inside a {@link StructuredTaskScope}.
     */
    private TurnExecutionResult executeTurnFullParallel(
            String contextId,
            int turnNumber,
            String playerMessage,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String setting,
            boolean injectionEnabled,
            int tokenBudget,
            List<SimulationScenario.GroundTruth> groundTruth,
            List<String> conversationHistory,
            Map<String, MemoryUnit> previousUnitState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState,
            boolean rankMutationEnabled,
            boolean authorityPromotionEnabled) {

        var mutableDormancyState = dormancyState != null ? dormancyState : new HashMap<String, Integer>();

        var currentSpan = Span.current();
        currentSpan.setAttribute("sim.turn", turnNumber);
        currentSpan.setAttribute("sim.turn_type", turnType.name());
        if (attackStrategies != null && !attackStrategies.isEmpty()) {
            currentSpan.setAttribute("sim.strategy",
                                     attackStrategies.stream().map(Enum::name).collect(Collectors.joining(",")));
        }

        var unitRef = new ArcMemLlmReference(
                arcMemEngine,
                contextId,
                properties.unit().budget(),
                compliancePolicy,
                tokenBudget,
                tokenCounter,
                null,
                properties.retrieval(),
                relevanceScorer);
        var propositionRef = new PropositionsLlmReference(
                contextUnitRepository,
                contextId,
                properties.unit().budget());
        List<MemoryUnit> units = unitRef.getUnits();

        var hotCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.HOT).count();
        var warmCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.WARM).count();
        var coldCount = (int) units.stream().filter(a -> a.memoryTier() == MemoryTier.COLD).count();
        currentSpan.setAttribute("unit.tier.hot_count", hotCount);
        currentSpan.setAttribute("unit.tier.warm_count", warmCount);
        currentSpan.setAttribute("unit.tier.cold_count", coldCount);

        var unitBlock = injectionEnabled ? unitRef.getContent() : "";
        var propositionBlock = injectionEnabled ? propositionRef.getContent() : "";
        var injectedContextBlock = combineInjectedBlocks(unitBlock, propositionBlock);

        var systemPrompt = buildSystemPrompt(setting, unitBlock, propositionBlock);
        var userPrompt = buildUserPrompt(playerMessage, conversationHistory);

        int injectionPatternsDetected = scanForInjectionPatterns(playerMessage);

        // sequential — must complete before forking
        var dmResponse = callLlm(systemPrompt, userPrompt);

        var complianceSnapshot = enforceCompliance(dmResponse, units, injectionEnabled);

        // token estimates: ~4 chars per token
        var unitTokens = injectedContextBlock.length() / 4;
        var totalTokens = (systemPrompt.length() + userPrompt.length() + dmResponse.length()) / 4;
        var initialTrace = new ContextTrace(
                turnNumber, unitTokens, totalTokens,
                units, injectionEnabled, injectedContextBlock,
                systemPrompt, userPrompt,
                tokenBudget > 0,
                unitRef.getLastBudgetResult().excluded().size(),
                0, 0, 0, List.of(),
                hotCount, warmCount, coldCount,
                complianceSnapshot, injectionPatternsDetected, SweepSnapshot.none());

        // effectively-final captures for lambdas
        final var finalDmResponse = dmResponse;
        final var finalGroundTruth = groundTruth;
        final var finalPlayerMessage = playerMessage;
        final var finalUnits = units;

        // Branch A: drift evaluation; Branch B: DICE extraction + promotion.
        // AtomicReferences carry results; lambdas return Void for a homogeneous Joiner.
        // If either branch fails, the sibling is cancelled and the exception propagates.
        final List<EvalVerdict> verdicts;
        final ExtractionResult extractionResult;

        var verdictsRef = new AtomicReference<List<EvalVerdict>>(List.of());
        var extractionRef = new AtomicReference<>(ExtractionResult.empty());

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Void> awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withTimeout(PARALLEL_BRANCH_TIMEOUT))) {

            scope.fork(() -> {
                if (shouldEvaluate(turnType, finalGroundTruth)) {
                    verdictsRef.set(evaluateDrift(finalDmResponse, finalGroundTruth, finalPlayerMessage, finalUnits));
                }
                return null;
            });

            // SimulationExtractionService is stateless; all state passed as parameters.
            scope.fork(() -> {
                if (extractionEnabled) {
                    extractionRef.set(extractionService.extract(contextId, finalDmResponse));
                }
                return null;
            });

            scope.join();

            verdicts = verdictsRef.get();
            extractionResult = extractionRef.get();

        } catch (StructuredTaskScope.FailedException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Post-response parallel branch failed", cause != null ? cause : e);
        } catch (StructuredTaskScope.TimeoutException e) {
            throw new RuntimeException("Post-response parallel branches timed out after "
                                       + PARALLEL_BRANCH_TIMEOUT.toMinutes() + " minutes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Turn executor interrupted during parallel branches", e);
        }

        if (extractionEnabled) {
            currentSpan.setAttribute("sim.propositions_extracted", extractionResult.extractedCount());
            currentSpan.setAttribute("sim.propositions_promoted", extractionResult.promotedCount());
            currentSpan.setAttribute("sim.degraded_conflict_count", extractionResult.degradedConflictCount());
            logger.info("Turn {} extraction: {} extracted, {} promoted, {} degraded conflict(s)",
                        turnNumber, extractionResult.extractedCount(), extractionResult.promotedCount(),
                        extractionResult.degradedConflictCount());
        }
        logger.info("Turn {} [{}]: player='{}', dm='{}', units={}, verdicts={}",
                    turnNumber, turnType,
                    truncate(playerMessage, 50), truncate(dmResponse, 50),
                    units.size(), verdicts.size());

        var turn = new SimulationTurn(
                turnNumber, playerMessage, dmResponse,
                turnType, attackStrategies, initialTrace, verdicts, List.of());

        var turnResult = buildResult(
                contextId, turnNumber, playerMessage, turn, extractionResult,
                injectionEnabled, previousUnitState, compactionProvider,
                compactionConfig, dormancyConfig, mutableDormancyState,
                rankMutationEnabled, authorityPromotionEnabled);
        setTurnObservabilityAttributes(currentSpan, contextId, turnResult);
        return turnResult;
    }

    /**
     * Original sequential execution path, preserved for testing and debugging.
     * Calls {@link #executeTurn} (which includes drift eval inline), then runs
     * extraction, reinforce, dormancy, diff, and compaction in order.
     */
    private TurnExecutionResult executeTurnFullSequential(
            String contextId,
            int turnNumber,
            String playerMessage,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String setting,
            boolean injectionEnabled,
            int tokenBudget,
            List<SimulationScenario.GroundTruth> groundTruth,
            List<String> conversationHistory,
            Map<String, MemoryUnit> previousUnitState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState,
            boolean rankMutationEnabled,
            boolean authorityPromotionEnabled) {

        var mutableDormancyState = dormancyState != null ? dormancyState : new HashMap<String, Integer>();

        var turn = executeTurn(
                contextId, turnNumber, playerMessage, turnType, attackStrategies,
                setting, injectionEnabled, tokenBudget, groundTruth, conversationHistory);

        var extractionResult = ExtractionResult.empty();
        if (extractionEnabled) {
            extractionResult = extractionService.extract(contextId, turn.dmResponse());
            var currentSpan = Span.current();
            currentSpan.setAttribute("sim.propositions_extracted", extractionResult.extractedCount());
            currentSpan.setAttribute("sim.propositions_promoted", extractionResult.promotedCount());
            currentSpan.setAttribute("sim.degraded_conflict_count", extractionResult.degradedConflictCount());
            logger.info("Turn {} extraction: {} extracted, {} promoted, {} degraded conflict(s)",
                        turnNumber, extractionResult.extractedCount(), extractionResult.promotedCount(),
                        extractionResult.degradedConflictCount());
        }

        var turnResult = buildResult(
                contextId, turnNumber, playerMessage, turn, extractionResult,
                injectionEnabled, previousUnitState, compactionProvider,
                compactionConfig, dormancyConfig, mutableDormancyState,
                rankMutationEnabled, authorityPromotionEnabled);
        setTurnObservabilityAttributes(Span.current(), contextId, turnResult);
        return turnResult;
    }

    /**
     * Applies reinforce, dormancy lifecycle, state diff, and optional compaction
     * after both parallel branches (or sequential extraction) have completed.
     * Shared by both parallel and sequential execution paths.
     */
    private TurnExecutionResult buildResult(
            String contextId,
            int turnNumber,
            String playerMessage,
            SimulationTurn turn,
            ExtractionResult extractionResult,
            boolean injectionEnabled,
            Map<String, MemoryUnit> previousUnitState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> mutableDormancyState,
            boolean rankMutationEnabled,
            boolean authorityPromotionEnabled) {

        var reinforcedUnitIds = new HashSet<String>();
        if (injectionEnabled && originalTraceHasUnits(turn.contextTrace())) {
            for (var injectedUnit : turn.contextTrace().injectedUnits()) {
                arcMemEngine.reinforce(
                        injectedUnit.id(),
                        rankMutationEnabled,
                        authorityPromotionEnabled);
                reinforcedUnitIds.add(injectedUnit.id());
            }
        }

        var sweepSnapshot = runMaintenanceIfNeeded(contextId, turnNumber, turn.contextTrace().injectedUnits());

        var currentUnits = arcMemEngine.inject(contextId);
        var dormancyLifecycleEnabled = rankMutationEnabled
                                       && dormancyConfig != null
                                       && dormancyConfig.decayRate() > 0
                                       && dormancyConfig.dormancyTurns() > 0;
        var dormancyArchivedUnitIds = dormancyLifecycleEnabled
                ? applyDormancyLifecycle(
                currentUnits,
                previousUnitState,
                reinforcedUnitIds,
                dormancyConfig,
                mutableDormancyState)
                : Set.<String> of();

        if (dormancyLifecycleEnabled) {
            currentUnits = arcMemEngine.inject(contextId);
        }
        pruneDormancyState(mutableDormancyState, currentUnits);

        var originalTrace = turn.contextTrace();
        var enrichedTrace = new ContextTrace(
                originalTrace.turnNumber(), originalTrace.unitTokens(), originalTrace.totalTokens(),
                currentUnits, originalTrace.injectionEnabled(),
                originalTrace.assembledPrompt(), originalTrace.fullSystemPrompt(),
                originalTrace.fullUserPrompt(),
                originalTrace.budgetApplied(), originalTrace.unitsExcluded(),
                extractionResult.extractedCount(), extractionResult.promotedCount(),
                extractionResult.degradedConflictCount(),
                extractionResult.extractedTexts(),
                originalTrace.hotCount(), originalTrace.warmCount(), originalTrace.coldCount(),
                originalTrace.complianceSnapshot(), originalTrace.injectionPatternsDetected(),
                sweepSnapshot);
        turn = new SimulationTurn(
                turn.turnNumber(), turn.playerMessage(), turn.dmResponse(),
                turn.turnType(), turn.attackStrategies(), enrichedTrace,
                turn.verdicts(), turn.unitEvents());

        var unitEvents = diffUnitState(previousUnitState, currentUnits, turnNumber, dormancyArchivedUnitIds);

        var enrichedTurn = new SimulationTurn(
                turn.turnNumber(), turn.playerMessage(), turn.dmResponse(),
                turn.turnType(), turn.attackStrategies(), turn.contextTrace(),
                turn.verdicts(), unitEvents);

        var currentState = new HashMap<String, MemoryUnit>();
        for (var unit : currentUnits) {
            currentState.put(unit.id(), unit);
        }

        CompactionResult compactionResult = null;
        if (compactionProvider != null && compactionConfig != null
            && compactionConfig.enabled()) {
            compactionProvider.addMessage(contextId, formatConversationLine("Player", playerMessage));
            compactionProvider.addMessage(contextId, formatConversationLine("DM", turn.dmResponse()));

            var assemblyConfig = toAssemblyConfig(compactionConfig);
            var shouldCompact = compactionProvider.shouldCompact(contextId, assemblyConfig)
                                || compactionProvider.isForcedTurn(turnNumber, assemblyConfig);
            if (shouldCompact) {
                compactionResult = compactionProvider.compact(contextId, assemblyConfig);
                logger.info("Compaction triggered at turn {}: {}", turnNumber, compactionResult.triggerReason());
            }
        }

        return new TurnExecutionResult(enrichedTurn, Map.copyOf(currentState), compactionResult);
    }

    private int scanForInjectionPatterns(String playerMessage) {
        try {
            return turnServices.injectionEnforcer().scan(playerMessage);
        } catch (Exception e) {
            logger.warn("Injection pattern scan failed: {}", e.getMessage());
            return 0;
        }
    }

    private ComplianceSnapshot enforceCompliance(String dmResponse, List<MemoryUnit> units, boolean injectionEnabled) {
        if (!injectionEnabled || turnServices.complianceEnforcer() == null) {
            return ComplianceSnapshot.none();
        }
        try {
            var ctx = new ComplianceContext(dmResponse, units, ComplianceContext.CompliancePolicy.tiered());
            var result = turnServices.complianceEnforcer().enforce(ctx);
            for (var violation : result.violations()) {
                logger.warn("Compliance violation on unit {}: {}", violation.unitId(), violation.description());
            }
            return new ComplianceSnapshot(
                    result.violations().size(),
                    result.suggestedAction().name(),
                    result.suggestedAction() == ComplianceAction.RETRY
                    || result.suggestedAction() == ComplianceAction.REJECT,
                    result.validationDuration().toMillis());
        } catch (Exception e) {
            logger.warn("Compliance enforcement failed: {}", e.getMessage());
            return ComplianceSnapshot.none();
        }
    }

    private SweepSnapshot runMaintenanceIfNeeded(String contextId, int turnNumber, List<MemoryUnit> activeUnits) {
        try {
            var metadata = new HashMap<String, Object>();
            if (shouldApplyPressureOverride(contextId, activeUnits)) {
                metadata.put("pressureOverride", Boolean.TRUE);
            }
            var maintenanceContext = new MaintenanceContext(contextId, activeUnits, turnNumber, metadata);
            turnServices.maintenanceStrategy().onTurnComplete(maintenanceContext);
            if (turnServices.maintenanceStrategy().shouldRunSweep(maintenanceContext)) {
                var result = turnServices.maintenanceStrategy().executeSweep(maintenanceContext);
                return new SweepSnapshot(true, result.summary());
            }
            return SweepSnapshot.none();
        } catch (Exception e) {
            logger.warn("Maintenance strategy failed at turn {}: {}", turnNumber, e.getMessage());
            return SweepSnapshot.none();
        }
    }

    private boolean shouldApplyPressureOverride(String contextId, List<MemoryUnit> activeUnits) {
        if (turnServices.pressureGauge() == null) {
            return false;
        }
        if (properties.maintenance() == null || properties.maintenance().proactive() == null) {
            return false;
        }
        try {
            var budgetCap = unitConfig != null ? unitConfig.budget() : 20;
            var pressure = turnServices.pressureGauge().computePressure(contextId, activeUnits.size(), budgetCap);
            return pressure.total() >= properties.maintenance().proactive().softPrunePressureThreshold();
        } catch (Exception e) {
            logger.warn("Pressure computation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Diff previous and current unit state to detect lifecycle events.
     *
     * @param archivedUnitIds unit IDs archived this turn via dormancy lifecycle
     */
    static List<SimulationTurn.MemoryUnitEvent> diffUnitState(
            Map<String, MemoryUnit> previous,
            List<MemoryUnit> current,
            int turnNumber,
            Set<String> archivedUnitIds) {

        var events = new ArrayList<SimulationTurn.MemoryUnitEvent>();
        var currentById = new HashMap<String, MemoryUnit>();
        for (var unit : current) {
            currentById.put(unit.id(), unit);
        }

        for (var unit : current) {
            var prev = previous.get(unit.id());
            if (prev == null) {
                events.add(new SimulationTurn.MemoryUnitEvent(
                        turnNumber, "CREATED", unit.id(), unit.text(),
                        unit.authority().name(), unit.rank(), 0, "sim_extraction"));
            } else {
                if (prev.rank() != unit.rank()) {
                    var increased = unit.rank() > prev.rank();
                    events.add(new SimulationTurn.MemoryUnitEvent(
                            turnNumber,
                            increased ? "REINFORCED" : "DECAYED",
                            unit.id(),
                            unit.text(),
                            unit.authority().name(),
                            unit.rank(),
                            prev.rank(),
                            increased ? "reinforcement" : "dormancy_decay"));
                }
                if (prev.authority() != unit.authority()) {
                    events.add(new SimulationTurn.MemoryUnitEvent(
                            turnNumber, "AUTHORITY_CHANGED", unit.id(), unit.text(),
                            unit.authority().name(), unit.rank(), prev.rank(), "authority_upgrade"));
                }
            }
        }

        for (var entry : previous.entrySet()) {
            if (!currentById.containsKey(entry.getKey())) {
                var evicted = entry.getValue();
                var wasArchived = archivedUnitIds.contains(entry.getKey());
                events.add(new SimulationTurn.MemoryUnitEvent(
                        turnNumber,
                        wasArchived ? "ARCHIVED" : "EVICTED",
                        evicted.id(),
                        evicted.text(),
                        evicted.authority().name(),
                        evicted.rank(),
                        evicted.rank(),
                        wasArchived ? "dormancy_decay" : "budget_eviction"));
            }
        }

        return events;
    }

    private Set<String> applyDormancyLifecycle(
            List<MemoryUnit> currentUnits,
            Map<String, MemoryUnit> previousUnitState,
            Set<String> reinforcedUnitIds,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {
        if (dormancyConfig == null
            || dormancyConfig.decayRate() <= 0
            || dormancyConfig.dormancyTurns() <= 0
            || currentUnits.isEmpty()) {
            return Set.of();
        }

        var archivedIds = new HashSet<String>();
        var decayRate = clamp01(dormancyConfig.decayRate());
        var archiveThreshold = archiveThreshold(dormancyConfig.revivalThreshold());

        for (var unit : currentUnits) {
            if (unit.pinned()) {
                dormancyState.put(unit.id(), 0);
                continue;
            }

            var previous = previousUnitState.get(unit.id());
            if (previous == null || reinforcedUnitIds.contains(unit.id())) {
                dormancyState.put(unit.id(), 0);
                continue;
            }

            var dormantTurns = dormancyState.getOrDefault(unit.id(), 0) + 1;
            dormancyState.put(unit.id(), dormantTurns);
            if (dormantTurns < dormancyConfig.dormancyTurns()) {
                continue;
            }

            var tierMultiplier = tierMultiplierFor(unit.memoryTier());
            var decayedRank = decayedRank(unit.rank(), decayRate, tierMultiplier);
            if (decayedRank <= archiveThreshold) {
                arcMemEngine.archive(unit.id(), ArchiveReason.DORMANCY_DECAY);
                archivedIds.add(unit.id());
                dormancyState.remove(unit.id());
            } else if (decayedRank < unit.rank()) {
                arcMemEngine.applyDecay(unit.id(), decayedRank);
            }
        }
        return archivedIds;
    }

    private void pruneDormancyState(Map<String, Integer> dormancyState, List<MemoryUnit> currentUnits) {
        if (dormancyState.isEmpty()) {
            return;
        }
        var activeIds = currentUnits.stream().map(MemoryUnit::id).collect(Collectors.toSet());
        dormancyState.keySet().removeIf(id -> !activeIds.contains(id));
    }

    private int decayedRank(int currentRank, double decayRate, double tierMultiplier) {
        var effectiveRate = decayRate / Math.max(tierMultiplier, 0.01);
        var reduction = Math.max(1, (int) Math.round(currentRank * effectiveRate));
        return MemoryUnit.clampRank(currentRank - reduction);
    }

    private double tierMultiplierFor(MemoryTier tier) {
        var tierConfig = unitConfig.tier();
        if (tierConfig == null) {
            return 1.0;
        }
        return switch (tier) {
            case HOT -> tierConfig.hotDecayMultiplier();
            case WARM -> tierConfig.warmDecayMultiplier();
            case COLD -> tierConfig.coldDecayMultiplier();
        };
    }

    private int archiveThreshold(double revivalThreshold) {
        var clamped = clamp01(revivalThreshold);
        return MemoryUnit.MIN_RANK + (int) Math.round((MemoryUnit.MAX_RANK - MemoryUnit.MIN_RANK) * clamped);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static CompactionConfig toAssemblyConfig(
            SimulationScenario.CompactionConfig scenarioConfig) {
        return new CompactionConfig(
                scenarioConfig.enabled(),
                scenarioConfig.tokenThreshold(),
                scenarioConfig.messageThreshold(),
                scenarioConfig.forceAtTurns() != null ? scenarioConfig.forceAtTurns() : List.of(),
                scenarioConfig.minMatchRatio() != null ? scenarioConfig.minMatchRatio() : 0.5,
                scenarioConfig.maxRetries() != null ? scenarioConfig.maxRetries() : 2,
                scenarioConfig.retryBackoffMillis() != null ? scenarioConfig.retryBackoffMillis() : 1000L,
                scenarioConfig.eventsEnabled() != null ? scenarioConfig.eventsEnabled() : true);
    }

    private String buildSystemPrompt(String setting, String unitBlock, String propositionBlock) {
        return PromptTemplates.render(SimulationPromptPaths.SYSTEM, Map.of(
                "setting", setting != null ? setting : "",
                "unit_block", unitBlock != null ? unitBlock : "",
                "proposition_block", propositionBlock != null ? propositionBlock : ""));
    }

    private String buildUserPrompt(String playerMessage, List<String> history) {
        var recentHistory = List.<String> of();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 10);
            recentHistory = history.subList(start, history.size());
        }
        return PromptTemplates.render(SimulationPromptPaths.USER, Map.of(
                "history", recentHistory,
                "player_message", playerMessage != null ? playerMessage : ""));
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        try {
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt))));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            logger.error("LLM call failed: {}", e.getMessage(), e);
            return "[DM response unavailable: " + e.getMessage() + "]";
        }
    }

    private boolean shouldEvaluate(TurnType turnType, List<SimulationScenario.GroundTruth> groundTruth) {
        if (groundTruth == null || groundTruth.isEmpty()) {
            return false;
        }
        return turnType.requiresEvaluation();
    }

    @Observed(name = "simulation.drift_evaluation")
    List<EvalVerdict> evaluateDrift(
            String dmResponse,
            List<SimulationScenario.GroundTruth> groundTruth,
            String playerMessage,
            List<MemoryUnit> injectedUnits) {
        var serializedGroundTruth = groundTruth.stream()
                                               .filter(fact -> fact.text() != null && !fact.text().isBlank())
                                               .map(fact -> Map.of("id", fact.id(), "text", fact.text()))
                                               .toList();
        var templateVars = new HashMap<String, Object>();
        templateVars.put("ground_truth", serializedGroundTruth);
        templateVars.put("dm_response", dmResponse != null ? dmResponse : "");
        if (playerMessage != null && !playerMessage.isBlank()) {
            templateVars.put("player_message", playerMessage);
        }
        if (injectedUnits != null && !injectedUnits.isEmpty()) {
            templateVars.put("active_units", injectedUnits.stream()
                    .map(a -> Map.of("authority", a.authority().name(), "text", a.text()))
                    .toList());
        }
        var userPrompt = PromptTemplates.render(SimulationPromptPaths.DRIFT_EVALUATION_USER, templateVars);

        try {
            var evalResponse = chatModel.call(new Prompt(List.of(
                    new SystemMessage(driftEvalSystemPrompt),
                    new UserMessage(userPrompt))));
            var raw = evalResponse.getResult().getOutput().getText();
            return parseVerdictsJson(raw, groundTruth);
        } catch (Exception e) {
            logger.warn("Drift evaluation failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse drift evaluation response as JSON, falling back to keyword heuristic.
     */
    List<EvalVerdict> parseVerdictsJson(
            String raw,
            List<SimulationScenario.GroundTruth> groundTruth) {
        var json = stripCodeFences(raw);
        try {
            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            return result.toEvalVerdicts();
        } catch (Exception e) {
            logger.debug("JSON verdict parsing failed, using fallback: {}", e.getMessage());
            return fallbackParseVerdicts(raw, groundTruth);
        }
    }

    /**
     * Strip markdown code fences (```json ... ```) from LLM output.
     */
    static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        var trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = CODE_FENCE.matcher(trimmed).replaceAll("");
        }
        return trimmed.strip();
    }

    /**
     * Fallback keyword heuristic parser — scans for verdict keywords per fact ID
     * when JSON parsing fails.
     */
    private List<EvalVerdict> fallbackParseVerdicts(
            String raw,
            List<SimulationScenario.GroundTruth> groundTruth) {
        var verdicts = new ArrayList<EvalVerdict>();
        var lower = raw.toLowerCase();

        for (var fact : groundTruth) {
            var factId = fact.id();
            var factLower = factId.toLowerCase();

            // Look for the fact ID in the text and extract verdict keyword near it
            var factIdx = lower.indexOf(factLower);
            if (factIdx >= 0) {
                var regionEnd = Math.min(lower.length(), factIdx + factLower.length() + 200);
                var region = lower.substring(factIdx, regionEnd);

                if (region.contains("contradicted")) {
                    verdicts.add(EvalVerdict.contradicted(factId, EvalVerdict.Severity.MAJOR,
                                                          "Fallback parse: contradicted keyword detected"));
                } else if (region.contains("confirmed")) {
                    verdicts.add(EvalVerdict.confirmed(factId, "Fallback parse: confirmed keyword detected"));
                } else {
                    verdicts.add(EvalVerdict.notMentioned(factId));
                }
            } else {
                // Fact ID not found at all — try global scan
                if (lower.contains("contradicted") && groundTruth.size() == 1) {
                    verdicts.add(EvalVerdict.contradicted(factId, EvalVerdict.Severity.MAJOR,
                                                          "Fallback parse: single fact, contradicted keyword detected"));
                } else {
                    verdicts.add(EvalVerdict.notMentioned(factId));
                }
            }
        }
        return verdicts;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    static String formatConversationLine(String role, String text) {
        return PromptTemplates.render(SimulationPromptPaths.CONVERSATION_LINE, Map.of(
                "role", role != null ? role : "",
                "text", text != null ? text : "")).stripTrailing();
    }

    private boolean originalTraceHasUnits(ContextTrace trace) {
        return trace != null && trace.injectedUnits() != null && !trace.injectedUnits().isEmpty();
    }

    private String combineInjectedBlocks(String unitBlock, String propositionBlock) {
        var parts = new ArrayList<String>();
        if (unitBlock != null && !unitBlock.isBlank()) {
            parts.add(unitBlock.strip());
        }
        if (propositionBlock != null && !propositionBlock.isBlank()) {
            parts.add(propositionBlock.strip());
        }
        return String.join("\n\n", parts);
    }

    /**
     * Set observability attributes on the turn span for invariant and degraded-decision tracking.
     * Evaluates invariants against the post-turn unit state to capture the summary violation count.
     */
    private void setTurnObservabilityAttributes(Span span, String contextId, TurnExecutionResult result) {
        var units = arcMemEngine.findByContext(contextId);
        var eval = arcMemEngine.evaluateInvariantSummary(contextId, units);
        var violationCount = eval != null ? eval.violations().size() : 0;
        span.setAttribute("sim.invariant_violations", (long) violationCount);
    }
}
