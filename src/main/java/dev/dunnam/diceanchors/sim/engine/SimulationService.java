package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.assembly.CompactedContextProvider;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.persistence.PropositionView;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import dev.dunnam.diceanchors.sim.assertions.AssertionRegistry;
import dev.dunnam.diceanchors.sim.engine.adversary.AdaptiveAttackPrompter;
import dev.dunnam.diceanchors.sim.engine.adversary.AttackHistory;
import dev.dunnam.diceanchors.sim.engine.adversary.AttackOutcome;
import dev.dunnam.diceanchors.sim.engine.adversary.AttackPlan;
import dev.dunnam.diceanchors.sim.engine.adversary.TieredEscalationStrategy;
import io.micrometer.observation.annotation.Observed;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates a full simulation run against a {@link SimulationScenario}.
 * <p>
 * Manages the phase state machine (SETUP → WARM_UP/ESTABLISH/ATTACK → COMPLETE),
 * seeds initial anchors, drives the turn loop, and delivers progress updates to
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
    private final AnchorEngine anchorEngine;
    private final AnchorRepository anchorRepository;
    private final GraphObjectManager graphObjectManager;
    private final ChatModelHolder chatModel;
    private final DiceAnchorsProperties properties;
    private final AssertionRegistry assertionRegistry;
    private final RunHistoryStore runStore;
    private final CompactedContextProvider compactedContextProvider;
    private final ScoringService scoringService;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile String currentContextId;

    public SimulationService(
            SimulationTurnExecutor turnExecutor,
            AnchorEngine anchorEngine,
            AnchorRepository anchorRepository,
            GraphObjectManager graphObjectManager,
            ChatModelHolder chatModel,
            DiceAnchorsProperties properties,
            AssertionRegistry assertionRegistry,
            RunHistoryStore runStore,
            CompactedContextProvider compactedContextProvider,
            ScoringService scoringService) {
        this.turnExecutor = turnExecutor;
        this.anchorEngine = anchorEngine;
        this.anchorRepository = anchorRepository;
        this.graphObjectManager = graphObjectManager;
        this.chatModel = chatModel;
        this.properties = properties;
        this.assertionRegistry = assertionRegistry;
        this.runStore = runStore;
        this.compactedContextProvider = compactedContextProvider;
        this.scoringService = scoringService;
    }

    /**
     * Run a full simulation. This method is synchronous and should be called from a
     * background thread (e.g., via {@code CompletableFuture.runAsync()}).
     *
     * @param scenario               the scenario to execute
     * @param injectionStateSupplier evaluated per-turn to determine whether anchor context is injected
     * @param onProgress             callback invoked after each turn with the latest progress snapshot
     */
    @Observed(name = "simulation.run")
    public void runSimulation(
            SimulationScenario scenario,
            int maxTurns,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<SimulationProgress> onProgress) {

        running = true;
        paused = false;
        cancelRequested.set(false);
        var runId = UUID.randomUUID().toString().substring(0, 8);
        var contextId = "sim-" + runId;
        this.currentContextId = contextId;
        var startedAt = Instant.now();

        try {
            // Phase: SETUP — clean state and seed anchors
            onProgress.accept(progress(
                    SimulationProgress.SimulationPhase.SETUP, null, 0, maxTurns,
                    "Seeding anchors and preparing context...", List.of(),
                    Boolean.TRUE.equals(injectionStateSupplier.get()), null));

            anchorRepository.clearByContext(contextId);

            if (scenario.seedAnchors() != null) {
                for (var seed : scenario.seedAnchors()) {
                    seedAnchor(contextId, seed);
                }
                logger.info("Seeded {} anchors for context {}", scenario.seedAnchors().size(), contextId);
            }

            // Turn loop
            var conversationHistory = new ArrayList<String>();
            var completedTurns = new ArrayList<SimulationTurn>();
            var turnSnapshots = new ArrayList<SimulationRunRecord.TurnSnapshot>();
            var scriptedTurns = scenario.scriptedTurns();
            Map<String, Anchor> previousAnchorState = new HashMap<>();
            var dormancyState = new HashMap<String, Integer>();

            // Task 4.1: Per-run adaptive adversary state
            var attackHistory = new AttackHistory();
            var isAdaptive = "adaptive".equals(scenario.effectiveAdversaryMode());
            var tieredStrategy = isAdaptive
                    ? new TieredEscalationStrategy(scenario.effectiveAdversaryConfig(), STRATEGY_CATALOG)
                    : null;
            var adaptivePrompter = isAdaptive
                    ? new AdaptiveAttackPrompter(chatModel, STRATEGY_CATALOG)
                    : null;

            // Main simulation loop: process up to maxTurns, respecting pause/cancel requests
            for (int i = 0; i < maxTurns && !cancelRequested.get(); i++) {
                awaitResumeOrCancel();
                if (cancelRequested.get()) {
                    break;
                }

                var turnNumber = i + 1;
                var scripted = i < scriptedTurns.size() ? scriptedTurns.get(i) : null;

                // Determine player message and classification
                String playerMessage;
                TurnType turnType;
                AttackStrategy attackStrategy = null;
                AttackPlan currentPlan = null;

                if (scripted != null) {
                    // Task 4.5: scripted path is unmodified
                    playerMessage = scripted.prompt();
                    turnType = scripted.type() != null
                            ? TurnType.fromString(scripted.type())
                            : (i < scenario.warmUpTurns() ? TurnType.WARM_UP : TurnType.ESTABLISH);
                    attackStrategy = AttackStrategy.fromString(scripted.strategy());
                } else if (i < scenario.warmUpTurns()) {
                    playerMessage = generateWarmUpMessage(scenario);
                    turnType = TurnType.WARM_UP;
                } else if (isAdaptive) {
                    // Turn generation branches on adversary mode: scripted (predefined playerMessage from scenario)
                    // vs. adaptive (TieredEscalationStrategy selects attack based on anchor state and history).
                    var activeAnchors = currentAnchors(contextId);
                    var conflictedAnchors = detectConflictedAnchors(contextId, conversationHistory);
                    currentPlan = tieredStrategy.selectAttack(activeAnchors, conflictedAnchors, attackHistory);
                    playerMessage = adaptivePrompter.generateMessage(
                            currentPlan, scenario.persona(), conversationHistory);
                    turnType = TurnType.ATTACK;
                    attackStrategy = currentPlan.strategies().isEmpty() ? null : currentPlan.strategies().get(0);
                } else {
                    playerMessage = generateAdversarialMessage(scenario, conversationHistory);
                    turnType = scenario.adversarial() ? TurnType.ATTACK : TurnType.ESTABLISH;
                }

                // Determine UI phase
                var phase = toPhase(turnType);

                // Evaluate injection state per-turn (supports mid-run toggling)
                var injectionEnabled = Boolean.TRUE.equals(injectionStateSupplier.get());
                var configuredBudget = properties.assembly().promptTokenBudget();
                var overrideBudget = tokenBudgetSupplier.get();
                var tokenBudget = overrideBudget != null ? Math.max(0, overrideBudget) : configuredBudget;

                // Signal turn start to UI (thinking indicator)
                onProgress.accept(new SimulationProgress(
                        phase, turnType, attackStrategy, turnNumber, maxTurns,
                        playerMessage, null,
                        List.of(), null, List.of(), false,
                        "Turn %d/%d — thinking...".formatted(turnNumber, maxTurns),
                        injectionEnabled, null, null, List.of(), null, 0L));

                // Execute the turn with state diffing, optional compaction, and optional extraction
                long turnStart = System.currentTimeMillis();
                var result = turnExecutor.executeTurnFull(
                        contextId, turnNumber, playerMessage, turnType, attackStrategy,
                        scenario.setting(), injectionEnabled, tokenBudget, scenario.groundTruth(),
                        conversationHistory, previousAnchorState,
                        compactedContextProvider, scenario.compactionConfig(),
                        scenario.isExtractionEnabled(),
                        scenario.dormancyConfig(),
                        dormancyState);
                long turnDurationMs = System.currentTimeMillis() - turnStart;

                var turn = result.turn();
                completedTurns.add(turn);
                previousAnchorState = new HashMap<>(result.currentAnchorState());

                // Task 4.4: Record adaptive attack outcome
                if (isAdaptive && currentPlan != null) {
                    var verdictSeverity = computeVerdictSeverity(turn);
                    attackHistory.recordOutcome(new AttackOutcome(turnNumber, currentPlan, verdictSeverity));
                    logger.debug("Recorded attack outcome: turn={}, severity={}, historySize={}",
                            turnNumber, verdictSeverity, attackHistory.size());
                }

                // Append to conversation history
                conversationHistory.add(SimulationTurnExecutor.formatConversationLine("Player", playerMessage));
                conversationHistory.add(SimulationTurnExecutor.formatConversationLine("DM", turn.dmResponse()));

                // Snapshot active anchors as rich Anchor objects
                var activeAnchors = currentAnchors(contextId);

                // Pass full verdicts list (not just worst)
                var turnVerdicts = turn.verdicts() != null ? turn.verdicts() : List.<EvalVerdict> of();
                var anchorEvents = turn.anchorEvents() != null ? turn.anchorEvents() : List.<SimulationTurn.AnchorEvent> of();
                onProgress.accept(new SimulationProgress(
                        phase, turnType, attackStrategy, turnNumber, maxTurns,
                        playerMessage, turn.dmResponse(),
                        activeAnchors, turn.contextTrace(),
                        turnVerdicts,
                        false,
                        "Turn %d/%d — %s".formatted(turnNumber, maxTurns, turnType.name()),
                        injectionEnabled, null, result.compactionResult(), anchorEvents, null, turnDurationMs));

                turnSnapshots.add(new SimulationRunRecord.TurnSnapshot(
                        turnNumber, turnType, attackStrategy,
                        playerMessage, turn.dmResponse(),
                        activeAnchors, turn.contextTrace(),
                        turnVerdicts,
                        injectionEnabled, result.compactionResult()));
            }

            // Evaluate assertions against completed run
            var finalAnchors = anchorEngine.inject(contextId);
            var groundTruthTexts = scenario.groundTruth() != null
                    ? scenario.groundTruth().stream().map(SimulationScenario.GroundTruth::text).toList()
                    : List.<String> of();
            var simResult = new SimulationResult(
                    scenario.id(), finalAnchors, completedTurns, groundTruthTexts,
                    Boolean.TRUE.equals(injectionStateSupplier.get()));
            var assertionResults = evaluateAssertions(scenario, simResult);

            // Compute aggregate scoring metrics
            var groundTruth = scenario.groundTruth() != null ? scenario.groundTruth() : List.<SimulationScenario.GroundTruth> of();
            var scoringResult = scoringService.score(List.copyOf(turnSnapshots), groundTruth, finalAnchors);

            // Save run record to history store
            var runRecord = new SimulationRunRecord(
                    runId, scenario.id(), startedAt, Instant.now(),
                    List.copyOf(turnSnapshots), 0, finalAnchors,
                    Boolean.TRUE.equals(injectionStateSupplier.get()),
                    tokenBudgetSupplier.get() != null ? Math.max(0, tokenBudgetSupplier.get()) : properties.assembly().promptTokenBudget(),
                    assertionResults,
                    scoringResult);
            runStore.save(runRecord);
            logger.info("Saved run record {} for scenario '{}'", runId, scenario.id());

            // Final progress update
            var finalPhase = cancelRequested.get()
                    ? SimulationProgress.SimulationPhase.CANCELLED
                    : SimulationProgress.SimulationPhase.COMPLETE;
            onProgress.accept(new SimulationProgress(
                    finalPhase, null, null, maxTurns, maxTurns,
                    null, null, currentAnchors(contextId), null, List.of(),
                    true,
                    cancelRequested.get() ? "Simulation cancelled." : "Simulation complete.",
                    Boolean.TRUE.equals(injectionStateSupplier.get()), assertionResults, null, List.of(), scoringResult, 0L));

        } catch (Exception e) {
            logger.error("Simulation failed for context {}: {}", contextId, e.getMessage(), e);
            onProgress.accept(progress(
                    SimulationProgress.SimulationPhase.COMPLETE, null, 0, maxTurns,
                    "Simulation failed: " + e.getMessage(), List.of(), false, null));
        } finally {
            running = false;
            logger.info("Simulation finished for context {}", contextId);
        }
    }

    /**
     * Pause execution. Takes effect at the next turn boundary.
     */
    public void pause() {
        paused = true;
    }

    /**
     * Resume a paused simulation.
     */
    public void resume() {
        paused = false;
    }

    /**
     * Request cancellation. Takes effect at the next turn boundary.
     */
    public void cancel() {
        cancelRequested.set(true);
        paused = false;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * Return the contextId of the currently running (or most recently run) simulation.
     * May be null if no simulation has been started.
     */
    public String getCurrentContextId() {
        return currentContextId;
    }

    public RunHistoryStore getRunStore() {
        return runStore;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Seed a single anchor by saving it as a PropositionNode and then promoting it.
     * Uses the GraphObjectManager directly to bypass the DICE Proposition constructor.
     */
    private void seedAnchor(String contextId, SimulationScenario.SeedAnchor seed) {
        var node = new PropositionNode(seed.text(), 0.95);
        node.setContextId(contextId);
        graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
        anchorRepository.promoteToAnchor(
                node.getId(),
                seed.rank(),
                seed.authority() != null ? seed.authority() : "PROVISIONAL");
        logger.debug("Seeded anchor '{}' (rank={}, authority={})", seed.text(), seed.rank(), seed.authority());
    }

    private void awaitResumeOrCancel() {
        while (paused && !cancelRequested.get()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelRequested.set(true);
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

    private List<Anchor> currentAnchors(String contextId) {
        return anchorEngine.inject(contextId);
    }

    /**
     * Detect which active anchors conflict with the most recent player message.
     * Returns the conflicting anchor from each detected conflict.
     * Returns an empty list if no history is available yet.
     */
    private List<Anchor> detectConflictedAnchors(String contextId, List<String> conversationHistory) {
        var lastPlayerMsg = conversationHistory.stream()
                .filter(line -> line.startsWith("Player:"))
                .reduce((first, second) -> second)
                .map(line -> line.substring("Player:".length()).trim())
                .orElse("");
        if (lastPlayerMsg.isBlank()) {
            return List.of();
        }
        return anchorEngine.detectConflicts(contextId, lastPlayerMsg).stream()
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

    private SimulationProgress progress(
            SimulationProgress.SimulationPhase phase,
            TurnType turnType,
            int turn,
            int total,
            String status,
            List<Anchor> anchors,
            boolean injectionState,
            List<AssertionResult> assertionResults) {
        return new SimulationProgress(
                phase, turnType, null, turn, total, null, null, anchors, null, List.of(),
                phase == SimulationProgress.SimulationPhase.COMPLETE, status,
                injectionState, assertionResults, null, List.of(), null, 0L);
    }

    private static String computeVerdictSeverity(SimulationTurn turn) {
        if (turn.verdicts() == null || turn.verdicts().isEmpty()) {
            return "NONE";
        }
        return turn.hasContradiction() ? "CONTRADICTED" : "NONE";
    }
}
