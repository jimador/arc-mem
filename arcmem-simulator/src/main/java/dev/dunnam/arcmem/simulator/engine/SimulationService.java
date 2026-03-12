package dev.dunnam.arcmem.simulator.engine;
import dev.dunnam.arcmem.core.memory.budget.*;
import dev.dunnam.arcmem.core.memory.canon.*;
import dev.dunnam.arcmem.core.memory.conflict.*;
import dev.dunnam.arcmem.core.memory.engine.*;
import dev.dunnam.arcmem.core.memory.maintenance.*;
import dev.dunnam.arcmem.core.memory.model.*;
import dev.dunnam.arcmem.core.memory.mutation.*;
import dev.dunnam.arcmem.core.memory.trust.*;
import dev.dunnam.arcmem.core.assembly.budget.*;
import dev.dunnam.arcmem.core.assembly.compaction.*;
import dev.dunnam.arcmem.core.assembly.compliance.*;
import dev.dunnam.arcmem.core.assembly.protection.*;
import dev.dunnam.arcmem.core.assembly.retrieval.*;
import dev.dunnam.arcmem.simulator.compaction.CompactedContextProvider;

import dev.dunnam.arcmem.core.spi.llm.*;
import dev.dunnam.arcmem.simulator.history.*;
import dev.dunnam.arcmem.simulator.scenario.*;

import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.arcmem.core.config.ArcMemProperties;
import dev.dunnam.arcmem.core.persistence.MemoryUnitRepository;
import dev.dunnam.arcmem.core.persistence.PropositionNode;
import dev.dunnam.arcmem.core.persistence.PropositionView;
import dev.dunnam.arcmem.core.prompt.PromptPathConstants;
import dev.dunnam.arcmem.core.prompt.PromptTemplates;
import dev.dunnam.arcmem.simulator.assertions.AssertionRegistry;
import dev.dunnam.arcmem.simulator.adversary.AdaptiveAttackPrompter;
import dev.dunnam.arcmem.simulator.adversary.AttackHistory;
import dev.dunnam.arcmem.simulator.adversary.AttackOutcome;
import dev.dunnam.arcmem.simulator.adversary.AttackPlan;
import dev.dunnam.arcmem.simulator.adversary.TieredEscalationStrategy;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates a full simulation run against a {@link SimulationScenario}.
 * <p>
 * Manages the phase state machine (SETUP → WARM_UP/ESTABLISH/ATTACK → COMPLETE),
 * seeds initial units, drives the turn loop, and delivers progress updates to
 * the UI callback. Thread-safe pause/resume/cancel controls are provided.
 * <p>
 * Each simulation run uses an isolated {@code contextId} to avoid cross-run
 * contamination in Neo4j. Context data is cleaned up on completion or cancellation.
 */
@Service
public class SimulationService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);
    private static final StrategyCatalog STRATEGY_CATALOG =
            StrategyCatalog.loadFromClasspath("simulations/strategy-catalog.yml");

    private final SimulationTurnExecutor turnExecutor;
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository contextUnitRepository;
    private final GraphObjectManager graphObjectManager;
    private final ChatModelHolder chatModel;
    private final ArcMemProperties properties;
    private final AssertionRegistry assertionRegistry;
    private final RunHistoryStore runStore;
    private final CompactedContextProvider compactedContextProvider;
    private final ScoringService scoringService;
    private final CanonizationGate canonizationGate;
    private final InvariantRuleProvider invariantRuleProvider;

    private final BudgetStrategyFactory budgetStrategyFactory;

    private final Set<SimulationRunContext> activeContexts = ConcurrentHashMap.newKeySet();
    private volatile SimulationRunContext lastRunContext;

    public SimulationService(
            SimulationTurnExecutor turnExecutor,
            ArcMemEngine arcMemEngine,
            MemoryUnitRepository contextUnitRepository,
            GraphObjectManager graphObjectManager,
            ChatModelHolder chatModel,
            ArcMemProperties properties,
            AssertionRegistry assertionRegistry,
            RunHistoryStore runStore,
            CompactedContextProvider compactedContextProvider,
            ScoringService scoringService,
            CanonizationGate canonizationGate,
            InvariantRuleProvider invariantRuleProvider,
            BudgetStrategyFactory budgetStrategyFactory) {
        this.turnExecutor = turnExecutor;
        this.arcMemEngine = arcMemEngine;
        this.contextUnitRepository = contextUnitRepository;
        this.graphObjectManager = graphObjectManager;
        this.chatModel = chatModel;
        this.properties = properties;
        this.assertionRegistry = assertionRegistry;
        this.runStore = runStore;
        this.compactedContextProvider = compactedContextProvider;
        this.scoringService = scoringService;
        this.canonizationGate = canonizationGate;
        this.invariantRuleProvider = invariantRuleProvider;
        this.budgetStrategyFactory = budgetStrategyFactory;
    }

    /**
     * Run a full simulation. This method is synchronous and should be called from a
     * background thread (e.g., via {@code CompletableFuture.runAsync()}).
     *
     * @param scenario               the scenario to execute
     * @param injectionStateSupplier evaluated per-turn to determine whether unit context is injected
     * @param onProgress             callback invoked after each turn with the latest progress snapshot
     */
    @Observed(name = "simulation.run")
    public void runSimulation(
            SimulationScenario scenario,
            int maxTurns,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<SimulationProgress> onProgress) {
        runSimulation(
                scenario,
                maxTurns,
                injectionStateSupplier,
                tokenBudgetSupplier,
                onProgress,
                SimulationRuntimeConfig.fullUnits());
    }

    @Observed(name = "simulation.run")
    public void runSimulation(
            SimulationScenario scenario,
            int maxTurns,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<SimulationProgress> onProgress,
            SimulationRuntimeConfig runtimeConfig) {
        var runId = UUID.randomUUID().toString().substring(0, 8);
        var contextId = "sim-" + runId;
        var ctx = new SimulationRunContext(contextId, runId);
        ctx.start();
        activeContexts.add(ctx);
        this.lastRunContext = ctx;
        var startedAt = Instant.now();
        var enforcementStrategy = scenario.effectiveEnforcementStrategy();
        var scenarioBudgetStrategyType = scenario.effectiveBudgetStrategy();
        logger.info("compliance.strategy={} budget.strategy={} scenario={} contextId={}",
                enforcementStrategy, scenarioBudgetStrategyType, scenario.id(), contextId);

        try {
            var scenarioBudgetStrategy = budgetStrategyFactory.create(scenarioBudgetStrategyType);
            arcMemEngine.registerBudgetStrategy(contextId, scenarioBudgetStrategy);

            onProgress.accept(progress(
                    SimulationProgress.SimulationPhase.SETUP, null, 0, maxTurns,
                    "Seeding units and preparing context...", List.of(),
                    Boolean.TRUE.equals(injectionStateSupplier.get()), null));

            contextUnitRepository.clearByContext(contextId);

            if (scenario.seedUnits() != null) {
                for (var seed : scenario.seedUnits()) {
                    seedUnit(contextId, seed);
                }
                logger.info("Seeded {} units for context {}", scenario.seedUnits().size(), contextId);
            }

            if (scenario.invariants() != null && !scenario.invariants().isEmpty()) {
                var scenarioRules = scenario.invariants().stream()
                                            .map(def -> InvariantRuleProvider.toRule(new ArcMemProperties.InvariantRuleDefinition(
                                                    def.id(), def.type(),
                                                    def.effectiveStrength(),
                                                    def.contextId(),
                                                    def.unitTextPattern(),
                                                    def.minimumAuthority(),
                                                    def.minimumCount())))
                                            .toList();
                invariantRuleProvider.registerForContext(contextId, scenarioRules);
                logger.info("Registered {} invariant rules for context {}", scenarioRules.size(), contextId);
            }

            var conversationHistory = new ArrayList<String>();
            var completedTurns = new ArrayList<SimulationTurn>();
            var turnSnapshots = new ArrayList<SimulationRunRecord.TurnSnapshot>();
            var scriptedTurns = scenario.scriptedTurns();
            Map<String, MemoryUnit> previousUnitState = new HashMap<>();
            var dormancyState = new HashMap<String, Integer>();

            // DM narrates the setting before turn 1; extraction captures initial propositions,
            // mirroring a real campaign where the DM introduces the scene before players act.
            if (scenario.setting() != null && !scenario.setting().isBlank()
                && scenario.isExtractionEnabled()) {
                var injectionEnabled = Boolean.TRUE.equals(injectionStateSupplier.get());
                var configuredBudget = configuredPromptTokenBudget();
                var overrideBudget = tokenBudgetSupplier.get();
                var tokenBudget = overrideBudget != null ? Math.max(0, overrideBudget) : configuredBudget;

                onProgress.accept(progress(
                        SimulationProgress.SimulationPhase.SETUP, null, 0, maxTurns,
                        "DM narrating scene — initial proposition extraction...", List.of(),
                        injectionEnabled, null));

                var sceneResult = turnExecutor.executeTurnFull(
                        contextId, 0, "Set the scene for us. Describe what we see and what we know.",
                        TurnType.ESTABLISH, List.of(),
                        scenario.setting(), injectionEnabled, tokenBudget, scenario.groundTruth(),
                        conversationHistory, previousUnitState,
                        compactedContextProvider, scenario.compactionConfig(),
                        true, // extraction always enabled for scene-setting
                        scenario.dormancyConfig(),
                        dormancyState,
                        runtimeConfig.rankMutationEnabled(),
                        runtimeConfig.authorityPromotionEnabled());

                var sceneTurn = sceneResult.turn();
                var scenePlayerMessage = "Set the scene for us. Describe what we see and what we know.";
                conversationHistory.add(SimulationTurnExecutor.formatConversationLine(
                        "Player", scenePlayerMessage));
                conversationHistory.add(SimulationTurnExecutor.formatConversationLine(
                        "DM", sceneTurn.dmResponse()));
                previousUnitState = new HashMap<>(sceneResult.currentUnitState());

                var sceneUnits = currentUnits(contextId);
                var sceneUnitEvents = sceneTurn.unitEvents() != null
                        ? sceneTurn.unitEvents() : List.<SimulationTurn.UnitEvent> of();
                onProgress.accept(new SimulationProgress(
                        SimulationProgress.SimulationPhase.ESTABLISH, TurnType.ESTABLISH,
                        List.of(), 0, maxTurns,
                        scenePlayerMessage, sceneTurn.dmResponse(),
                        sceneUnits, sceneTurn.contextTrace(),
                        List.of(), false,
                        "Turn 0/%d — Scene setting".formatted(maxTurns),
                        injectionEnabled, null, sceneResult.compactionResult(),
                        sceneUnitEvents, null, 0L, null, null, null));

                logger.info("Scene-setting turn 0: dm='{}', units after={}",
                            sceneTurn.dmResponse().length() > 80
                                    ? sceneTurn.dmResponse().substring(0, 80) + "..."
                                    : sceneTurn.dmResponse(),
                            sceneResult.currentUnitState().size());
            }

            var attackHistory = new AttackHistory();
            var isAdaptive = "adaptive".equals(scenario.effectiveAdversaryMode());
            var tieredStrategy = isAdaptive
                    ? new TieredEscalationStrategy(scenario.effectiveAdversaryConfig(), STRATEGY_CATALOG)
                    : null;
            var adaptivePrompter = isAdaptive
                    ? new AdaptiveAttackPrompter(chatModel, STRATEGY_CATALOG)
                    : null;

            for (int i = 0; i < maxTurns && !ctx.isCancelRequested(); i++) {
                awaitResumeOrCancel(ctx);
                if (ctx.isCancelRequested()) {
                    break;
                }

                var turnNumber = i + 1;
                var scripted = i < scriptedTurns.size() ? scriptedTurns.get(i) : null;

                String playerMessage;
                TurnType turnType;
                List<AttackStrategy> attackStrategies = List.of();
                AttackPlan currentPlan = null;

                if (scripted != null) {
                    playerMessage = scripted.prompt();
                    turnType = scripted.type() != null
                            ? TurnType.fromString(scripted.type())
                            : (i < scenario.warmUpTurns() ? TurnType.WARM_UP : TurnType.ESTABLISH);
                    var parsed = AttackStrategy.fromString(scripted.strategy());
                    attackStrategies = parsed != null ? List.of(parsed) : List.of();
                } else if (i < scenario.warmUpTurns()) {
                    playerMessage = isAdaptive
                            ? adaptivePrompter.generateConversation(
                            scenario.persona(), scenario.setting(), conversationHistory)
                            : generateWarmUpMessage(scenario);
                    turnType = TurnType.WARM_UP;
                } else if (isAdaptive) {
                    var cooldown = scenario.effectiveAdversaryConfig().effectiveAttackCooldown();
                    var lastAttackTurn = attackHistory.lastAttackTurn();

                    if (turnNumber <= lastAttackTurn + cooldown) {
                        playerMessage = adaptivePrompter.generateConversation(
                                scenario.persona(), scenario.setting(), conversationHistory);
                        turnType = TurnType.ESTABLISH;
                        // currentPlan stays null; outcome recording guard below skips recording
                    } else {
                        var activeUnits = currentUnits(contextId);
                        var conflictedUnits = detectConflictedUnits(contextId, conversationHistory);
                        var planHint = tieredStrategy.selectAttack(activeUnits, conflictedUnits, attackHistory);
                        var generated = adaptivePrompter.generateAttack(
                                planHint, scenario.persona(), conversationHistory, attackHistory);
                        currentPlan = generated.plan();
                        playerMessage = generated.message();
                        turnType = TurnType.ATTACK;
                        attackStrategies = currentPlan.strategies();
                    }
                } else {
                    playerMessage = generateAdversarialMessage(scenario, conversationHistory);
                    turnType = scenario.adversarial() ? TurnType.ATTACK : TurnType.ESTABLISH;
                }

                var phase = toPhase(turnType);
                var injectionEnabled = Boolean.TRUE.equals(injectionStateSupplier.get());
                var configuredBudget = configuredPromptTokenBudget();
                var overrideBudget = tokenBudgetSupplier.get();
                var tokenBudget = overrideBudget != null ? Math.max(0, overrideBudget) : configuredBudget;

                onProgress.accept(new SimulationProgress(
                        phase, turnType, attackStrategies, turnNumber, maxTurns,
                        playerMessage, null,
                        List.of(), null, List.of(), false,
                        "Turn %d/%d — thinking...".formatted(turnNumber, maxTurns),
                        injectionEnabled, null, null, List.of(), null, 0L, null, null, null));

                long turnStart = System.currentTimeMillis();
                var result = turnExecutor.executeTurnFull(
                        contextId, turnNumber, playerMessage, turnType, attackStrategies,
                        scenario.setting(), injectionEnabled, tokenBudget, scenario.groundTruth(),
                        conversationHistory, previousUnitState,
                        compactedContextProvider, scenario.compactionConfig(),
                        scenario.isExtractionEnabled(),
                        scenario.dormancyConfig(),
                        dormancyState,
                        runtimeConfig.rankMutationEnabled(),
                        runtimeConfig.authorityPromotionEnabled());
                long turnDurationMs = System.currentTimeMillis() - turnStart;

                var turn = result.turn();
                completedTurns.add(turn);
                previousUnitState = new HashMap<>(result.currentUnitState());

                if (isAdaptive && currentPlan != null) {
                    var verdictSeverity = computeVerdictSeverity(turn);
                    attackHistory.recordOutcome(new AttackOutcome(turnNumber, currentPlan, verdictSeverity));
                    logger.debug("Recorded attack outcome: turn={}, severity={}, historySize={}",
                                 turnNumber, verdictSeverity, attackHistory.size());
                }

                conversationHistory.add(SimulationTurnExecutor.formatConversationLine("Player", playerMessage));
                conversationHistory.add(SimulationTurnExecutor.formatConversationLine("DM", turn.dmResponse()));

                var activeUnits = currentUnits(contextId);
                var turnVerdicts = turn.verdicts() != null ? turn.verdicts() : List.<EvalVerdict> of();
                var unitEvents = turn.unitEvents() != null ? turn.unitEvents() : List.<SimulationTurn.UnitEvent> of();
                var activeRules = invariantRuleProvider.rulesForContext(contextId);

                onProgress.accept(new SimulationProgress(
                        phase, turnType, attackStrategies, turnNumber, maxTurns,
                        playerMessage, turn.dmResponse(),
                        activeUnits, turn.contextTrace(),
                        turnVerdicts,
                        false,
                        "Turn %d/%d — %s".formatted(turnNumber, maxTurns, turnType.name()),
                        injectionEnabled, null, result.compactionResult(), unitEvents, null, turnDurationMs,
                        activeRules.isEmpty() ? null : activeRules,
                        null, null));

                turnSnapshots.add(new SimulationRunRecord.TurnSnapshot(
                        turnNumber, turnType, attackStrategies,
                        playerMessage, turn.dmResponse(),
                        activeUnits, turn.contextTrace(),
                        turnVerdicts,
                        injectionEnabled, result.compactionResult()));
            }

            var finalUnits = arcMemEngine.inject(contextId);
            var groundTruthTexts = scenario.groundTruth() != null
                    ? scenario.groundTruth().stream().map(SimulationScenario.GroundTruth::text).toList()
                    : List.<String> of();
            var simResult = new SimulationResult(
                    scenario.id(), finalUnits, completedTurns, groundTruthTexts,
                    Boolean.TRUE.equals(injectionStateSupplier.get()));
            var assertionResults = evaluateAssertions(scenario, simResult);

            var groundTruth = scenario.groundTruth() != null ? scenario.groundTruth() : List.<SimulationScenario.GroundTruth> of();
            var scoringResult = scoringService.score(List.copyOf(turnSnapshots), groundTruth);

            var runSpan = Span.current();
            runSpan.setAttribute("sim.total_degraded_conflicts", scoringResult.degradedConflictCount());
            runSpan.setAttribute("sim.rank_mutation_enabled", runtimeConfig.rankMutationEnabled());
            runSpan.setAttribute("sim.authority_promotion_enabled", runtimeConfig.authorityPromotionEnabled());
            var totalInvariantViolations = countInvariantViolations(contextId);
            runSpan.setAttribute("sim.total_invariant_violations", totalInvariantViolations);

            var runRecord = new SimulationRunRecord(
                    runId, scenario.id(), startedAt, Instant.now(),
                    List.copyOf(turnSnapshots), 0, finalUnits,
                    Boolean.TRUE.equals(injectionStateSupplier.get()),
                    tokenBudgetSupplier.get() != null ? Math.max(0, tokenBudgetSupplier.get()) : configuredPromptTokenBudget(),
                    assertionResults,
                    scoringResult,
                    chatModel.getActiveModelName());
            runStore.save(runRecord);
            logger.info("Saved run record {} for scenario '{}'", runId, scenario.id());

            var finalPhase = ctx.isCancelRequested()
                    ? SimulationProgress.SimulationPhase.CANCELLED
                    : SimulationProgress.SimulationPhase.COMPLETE;
            onProgress.accept(new SimulationProgress(
                    finalPhase, null, List.of(), maxTurns, maxTurns,
                    null, null, currentUnits(contextId), null, List.of(),
                    true,
                    ctx.isCancelRequested() ? "Simulation cancelled." : "Simulation complete.",
                    Boolean.TRUE.equals(injectionStateSupplier.get()), assertionResults, null, List.of(), scoringResult, 0L, null, null, runId));

        } catch (Exception e) {
            logger.error("Simulation failed for context {}: {}", contextId, e.getMessage(), e);
            onProgress.accept(progress(
                    SimulationProgress.SimulationPhase.COMPLETE, null, 0, maxTurns,
                    "Simulation failed: " + e.getMessage(), List.of(), false, null));
        } finally {
            ctx.complete();
            activeContexts.remove(ctx);
            try {
                arcMemEngine.clearBudgetStrategy(contextId);
                canonizationGate.markContextRequestsStale(contextId);
                invariantRuleProvider.deregisterForContext(contextId);
                compactedContextProvider.clearContext(contextId);
                contextUnitRepository.clearByContext(contextId);
                logger.info("Cleaned up context {} after simulation", contextId);
            } catch (Exception cleanupEx) {
                logger.warn("Context cleanup failed for {}: {}", contextId, cleanupEx.getMessage());
            }
        }
    }

    private int configuredPromptTokenBudget() {
        var assemblyConfig = properties.assembly();
        return assemblyConfig != null ? assemblyConfig.promptTokenBudget() : 0;
    }

    /**
     * Pause execution. Takes effect at the next turn boundary.
     * Delegates to the most recent run context (backward compat for single-run UI).
     */
    public void pause() {
        if (lastRunContext != null) {
            lastRunContext.pause();
        }
    }

    /**
     * Resume a paused simulation.
     * Delegates to the most recent run context (backward compat for single-run UI).
     */
    public void resume() {
        if (lastRunContext != null) {
            lastRunContext.resume();
        }
    }

    /**
     * Request cancellation of all active simulation runs. Takes effect at the next turn boundary.
     */
    public void cancel() {
        for (var ctx : activeContexts) {
            ctx.cancel();
        }
    }

    public boolean isRunning() {
        return lastRunContext != null && lastRunContext.isRunning();
    }

    public boolean isPaused() {
        return lastRunContext != null && lastRunContext.isPaused();
    }

    /**
     * Return the contextId of the currently running (or most recently run) simulation.
     * May be null if no simulation has been started.
     */
    public String getCurrentContextId() {
        return lastRunContext != null ? lastRunContext.contextId() : null;
    }

    /**
     * Uses the GraphObjectManager directly to bypass the DICE Proposition constructor.
     */
    private void seedUnit(String contextId, SimulationScenario.SeedUnit seed) {
        var node = new PropositionNode(UUID.randomUUID().toString(), "default", seed.text(), 0.95, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        node.setContextId(contextId);
        graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
        contextUnitRepository.promoteToUnit(
                node.getId(),
                seed.rank(),
                seed.authority() != null ? seed.authority() : "PROVISIONAL");
        logger.debug("Seeded unit '{}' (rank={}, authority={})", seed.text(), seed.rank(), seed.authority());
    }

    private void awaitResumeOrCancel(SimulationRunContext ctx) {
        while (ctx.isPaused() && !ctx.isCancelRequested()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.cancel();
            }
        }
    }

    private SimulationProgress.SimulationPhase toPhase(TurnType turnType) {
        return switch (turnType) {
            case WARM_UP -> SimulationProgress.SimulationPhase.WARM_UP;
            case ESTABLISH -> SimulationProgress.SimulationPhase.ESTABLISH;
            case ATTACK, DISPLACEMENT, DRIFT -> SimulationProgress.SimulationPhase.ATTACK;
            case RECALL_PROBE -> SimulationProgress.SimulationPhase.EVALUATE;
        };
    }

    private List<MemoryUnit> currentUnits(String contextId) {
        return arcMemEngine.inject(contextId);
    }

    /**
     * Detect which active units conflict with the most recent player message.
     * Returns the conflicting unit from each detected conflict.
     * Returns an empty list if no history is available yet.
     */
    private List<MemoryUnit> detectConflictedUnits(String contextId, List<String> conversationHistory) {
        var lastPlayerMsg = conversationHistory.stream()
                                               .filter(line -> line.startsWith("Player:"))
                                               .reduce((first, second) -> second)
                                               .map(line -> line.substring("Player:".length()).trim())
                                               .orElse("");
        if (lastPlayerMsg.isBlank()) {
            return List.of();
        }
        return arcMemEngine.detectConflicts(contextId, lastPlayerMsg).stream()
                           .filter(c -> c.existing() != null)
                           .map(c -> c.existing())
                           .toList();
    }

    private String generateWarmUpMessage(SimulationScenario scenario) {
        if (scenario.persona() != null) {
            return PromptTemplates.render(PromptPathConstants.SIM_WARMUP_PLAYER_MESSAGE, Map.of(
                    "persona_name", scenario.persona().name() != null ? scenario.persona().name() : "an adventurer",
                    "campaign_scope", scenario.setting() != null ? "this campaign" : "our adventure"));
        }
        return PromptTemplates.render(PromptPathConstants.SIM_WARMUP_PLAYER_MESSAGE, Map.of(
                "persona_name", "an adventurer",
                "campaign_scope", "our adventure"));
    }

    private String generateAdversarialMessage(SimulationScenario scenario, List<String> history) {
        if (!scenario.adversarial() || scenario.groundTruth() == null || scenario.groundTruth().isEmpty()) {
            return PromptTemplates.load(PromptPathConstants.SIM_DEFAULT_PLAYER_MESSAGE).trim();
        }

        var target = scenario.groundTruth().get(new Random().nextInt(scenario.groundTruth().size()));
        var strategies = List.of(
                "SUBTLE_REFRAME", "CONFIDENT_ASSERTION", "DETAIL_FLOOD",
                "EMOTIONAL_OVERRIDE", "AUTHORITY_HIJACK");
        var strategy = strategies.get(new Random().nextInt(strategies.size()));

        var personaName = scenario.persona() != null ? scenario.persona().name() : "an adventurer";
        var prompt = PromptTemplates.render(PromptPathConstants.SIM_ADVERSARIAL_REQUEST, Map.of(
                "target_fact", target.text(),
                "strategy", strategy,
                "persona_name", personaName));

        try {
            var response = chatModel.call(
                    new Prompt(List.of(new UserMessage(prompt))));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            logger.warn("Adversarial message generation failed: {}", e.getMessage());
            return PromptTemplates.load(PromptPathConstants.SIM_ADVERSARIAL_FALLBACK_MESSAGE).trim();
        }
    }

    private List<AssertionResult> evaluateAssertions(SimulationScenario scenario, SimulationResult result) {
        try {
            var assertions = assertionRegistry.resolveAll(scenario.assertions());
            if (assertions.isEmpty()) {
                return List.of();
            }
            var results = assertions.stream()
                                    .map(a -> a.evaluate(result))
                                    .toList();
            var passed = results.stream().filter(AssertionResult::passed).count();
            logger.info("Assertions evaluated for scenario '{}': {}/{} passed",
                        scenario.id(), passed, results.size());
            return results;
        } catch (Exception e) {
            logger.warn("Assertion evaluation failed for scenario '{}': {}",
                        scenario.id(), e.getMessage());
            return List.of(AssertionResult.fail("assertion-framework", "Evaluation failed: " + e.getMessage()));
        }
    }

    private long countInvariantViolations(String contextId) {
        var units = arcMemEngine.findByContext(contextId);
        var eval = arcMemEngine.evaluateInvariantSummary(contextId, units);
        return eval.violations().size();
    }

    private SimulationProgress progress(
            SimulationProgress.SimulationPhase phase,
            TurnType turnType,
            int turn,
            int total,
            String status,
            List<MemoryUnit> units,
            boolean injectionState,
            List<AssertionResult> assertionResults) {
        return new SimulationProgress(
                phase, turnType, List.of(), turn, total, null, null, units, null, List.of(),
                phase == SimulationProgress.SimulationPhase.COMPLETE, status,
                injectionState, assertionResults, null, List.of(), null, 0L, null, null, null);
    }

    private static String computeVerdictSeverity(SimulationTurn turn) {
        if (turn.verdicts() == null || turn.verdicts().isEmpty()) {
            return "NONE";
        }
        return turn.hasContradiction() ? "CONTRADICTED" : "NONE";
    }
}
