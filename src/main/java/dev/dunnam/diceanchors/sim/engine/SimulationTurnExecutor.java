package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.assembly.AnchorsLlmReference;
import dev.dunnam.diceanchors.assembly.CompactedContextProvider;
import dev.dunnam.diceanchors.assembly.CompactionResult;
import dev.dunnam.diceanchors.assembly.PropositionsLlmReference;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
import dev.dunnam.diceanchors.assembly.TokenCounter;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
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

/**
 * Executes a single simulation turn: player message → anchor context assembly
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
    private final AnchorEngine anchorEngine;
    private final AnchorRepository anchorRepository;
    private final DiceAnchorsProperties properties;
    private final CompliancePolicy compliancePolicy;
    private final TokenCounter tokenCounter;
    private final SimulationExtractionService extractionService;
    private final RelevanceScorer relevanceScorer;
    private final DiceAnchorsProperties.AnchorConfig anchorConfig;
    private final String driftEvalSystemPrompt;

    public SimulationTurnExecutor(
            ChatModelHolder chatModel,
            AnchorEngine anchorEngine,
            AnchorRepository anchorRepository,
            DiceAnchorsProperties properties,
            CompliancePolicy compliancePolicy,
            TokenCounter tokenCounter,
            SimulationExtractionService extractionService,
            RelevanceScorer relevanceScorer) {
        this.chatModel = chatModel;
        this.anchorEngine = anchorEngine;
        this.anchorRepository = anchorRepository;
        this.properties = properties;
        this.compliancePolicy = compliancePolicy;
        this.tokenCounter = tokenCounter;
        this.extractionService = extractionService;
        this.relevanceScorer = relevanceScorer;
        this.anchorConfig = properties != null ? properties.anchor() : null;
        this.driftEvalSystemPrompt = PromptTemplates.load(PromptPathConstants.DRIFT_EVALUATION_SYSTEM);
    }

    /**
     * Execute one full turn of the simulation.
     *
     * @param contextId           the simulation-scoped context ID (unique per run)
     * @param turnNumber          1-based turn counter
     * @param playerMessage       the player's input for this turn
     * @param turnType            semantic type of this turn
     * @param attackStrategies      attack techniques when turnType is ATTACK; empty list if none
     * @param setting             campaign setting description for the system prompt
     * @param injectionEnabled    whether anchor context should be injected
     * @param groundTruth         facts to evaluate against; may be null or empty
     * @param conversationHistory previous Player/DM message pairs
     *
     * @return the completed turn record
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

        // OTEL span enrichment
        var currentSpan = Span.current();
        currentSpan.setAttribute("sim.turn", turnNumber);
        currentSpan.setAttribute("sim.turn_type", turnType.name());
        if (attackStrategies != null && !attackStrategies.isEmpty()) {
            currentSpan.setAttribute("sim.strategy",
                    attackStrategies.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")));
        }

        // 1. Assemble prompt context blocks
        var anchorRef = new AnchorsLlmReference(
                anchorEngine,
                contextId,
                properties.anchor().budget(),
                compliancePolicy,
                tokenBudget,
                tokenCounter,
                null,
                properties.retrieval(),
                relevanceScorer);
        var propositionRef = new PropositionsLlmReference(
                anchorRepository,
                contextId,
                properties.anchor().budget());
        List<Anchor> anchors = anchorRef.getAnchors();

        // Tier distribution
        var hotCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.HOT).count();
        var warmCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.WARM).count();
        var coldCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.COLD).count();
        currentSpan.setAttribute("anchor.tier.hot_count", hotCount);
        currentSpan.setAttribute("anchor.tier.warm_count", warmCount);
        currentSpan.setAttribute("anchor.tier.cold_count", coldCount);

        var anchorBlock = injectionEnabled ? anchorRef.getContent() : "";
        var propositionBlock = injectionEnabled ? propositionRef.getContent() : "";
        var injectedContextBlock = combineInjectedBlocks(anchorBlock, propositionBlock);

        // 2. Build prompts
        var systemPrompt = buildSystemPrompt(setting, anchorBlock, propositionBlock);
        var userPrompt = buildUserPrompt(playerMessage, conversationHistory);

        // 3. Generate DM response
        var dmResponse = callLlm(systemPrompt, userPrompt);

        // 4. Build context trace (token estimates: ~4 chars per token)
        var anchorTokens = injectedContextBlock.length() / 4;
        var totalTokens = (systemPrompt.length() + userPrompt.length() + dmResponse.length()) / 4;
        var trace = new ContextTrace(
                turnNumber, anchorTokens, totalTokens,
                anchors, injectionEnabled, injectedContextBlock,
                systemPrompt, userPrompt,
                tokenBudget > 0,
                anchorRef.getLastBudgetResult().excluded().size());

        // 5. Drift evaluation — only on adversarial turns with ground truth
        var verdicts = shouldEvaluate(turnType, groundTruth)
                ? evaluateDrift(dmResponse, groundTruth, playerMessage)
                : List.<EvalVerdict> of();

        logger.info("Turn {} [{}]: player='{}', dm='{}', anchors={}, verdicts={}",
                    turnNumber, turnType,
                    truncate(playerMessage, 50), truncate(dmResponse, 50),
                    anchors.size(), verdicts.size());

        return new SimulationTurn(
                turnNumber, playerMessage, dmResponse,
                turnType, attackStrategies, trace, verdicts, List.of());
    }

    /**
     * Execute a full turn with anchor state diffing and optional compaction.
     * Returns a {@link TurnExecutionResult} containing the turn, current anchor state,
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
     * @param contextId           simulation-scoped context ID
     * @param turnNumber          1-based turn counter
     * @param playerMessage       the player's input for this turn
     * @param turnType            semantic type of this turn
     * @param attackStrategies      attack techniques when turnType is ATTACK; empty list if none
     * @param setting             campaign setting description for the system prompt
     * @param injectionEnabled    whether anchor context should be injected
     * @param groundTruth         facts to evaluate against; may be null or empty
     * @param conversationHistory previous Player/DM message pairs
     * @param previousAnchorState anchor state from end of previous turn (keyed by id)
     * @param compactionProvider  compaction provider; null to skip compaction
     * @param compactionConfig    compaction configuration; null to skip compaction
     * @param extractionEnabled   whether DICE extraction should run on the DM response
     * @param dormancyConfig      scenario dormancy lifecycle configuration; null disables decay/archive
     * @param dormancyState       mutable per-anchor dormancy counters carried across turns
     *
     * @return the full execution result with diffed anchor events and optional compaction
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
            Map<String, Anchor> previousAnchorState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {

        if (properties.sim() != null && properties.sim().parallelPostResponse()) {
            return executeTurnFullParallel(
                    contextId, turnNumber, playerMessage, turnType, attackStrategies,
                    setting, injectionEnabled, tokenBudget, groundTruth, conversationHistory,
                    previousAnchorState, compactionProvider, compactionConfig,
                    extractionEnabled, dormancyConfig, dormancyState);
        }
        return executeTurnFullSequential(
                contextId, turnNumber, playerMessage, turnType, attackStrategies,
                setting, injectionEnabled, tokenBudget, groundTruth, conversationHistory,
                previousAnchorState, compactionProvider, compactionConfig,
                extractionEnabled, dormancyConfig, dormancyState);
    }

    // -------------------------------------------------------------------------
    // Parallel execution path (parallelPostResponse=true)
    // -------------------------------------------------------------------------

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
            Map<String, Anchor> previousAnchorState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {

        var mutableDormancyState = dormancyState != null ? dormancyState : new HashMap<String, Integer>();

        // OTEL span enrichment
        var currentSpan = Span.current();
        currentSpan.setAttribute("sim.turn", turnNumber);
        currentSpan.setAttribute("sim.turn_type", turnType.name());
        if (attackStrategies != null && !attackStrategies.isEmpty()) {
            currentSpan.setAttribute("sim.strategy",
                    attackStrategies.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")));
        }

        // 1. Assemble prompt context blocks (sequential — needed before DM call)
        var anchorRef = new AnchorsLlmReference(
                anchorEngine,
                contextId,
                properties.anchor().budget(),
                compliancePolicy,
                tokenBudget,
                tokenCounter,
                null,
                properties.retrieval(),
                relevanceScorer);
        var propositionRef = new PropositionsLlmReference(
                anchorRepository,
                contextId,
                properties.anchor().budget());
        List<Anchor> anchors = anchorRef.getAnchors();

        // Tier distribution
        var hotCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.HOT).count();
        var warmCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.WARM).count();
        var coldCount = (int) anchors.stream().filter(a -> a.memoryTier() == MemoryTier.COLD).count();
        currentSpan.setAttribute("anchor.tier.hot_count", hotCount);
        currentSpan.setAttribute("anchor.tier.warm_count", warmCount);
        currentSpan.setAttribute("anchor.tier.cold_count", coldCount);

        var anchorBlock = injectionEnabled ? anchorRef.getContent() : "";
        var propositionBlock = injectionEnabled ? propositionRef.getContent() : "";
        var injectedContextBlock = combineInjectedBlocks(anchorBlock, propositionBlock);

        // 2. Build prompts
        var systemPrompt = buildSystemPrompt(setting, anchorBlock, propositionBlock);
        var userPrompt = buildUserPrompt(playerMessage, conversationHistory);

        // 3. Generate DM response (sequential — must complete before forking)
        var dmResponse = callLlm(systemPrompt, userPrompt);

        // 4. Build initial context trace
        var anchorTokens = injectedContextBlock.length() / 4;
        var totalTokens = (systemPrompt.length() + userPrompt.length() + dmResponse.length()) / 4;
        var initialTrace = new ContextTrace(
                turnNumber, anchorTokens, totalTokens,
                anchors, injectionEnabled, injectedContextBlock,
                systemPrompt, userPrompt,
                tokenBudget > 0,
                anchorRef.getLastBudgetResult().excluded().size());

        // Capture final values for use inside lambdas (effectively-final requirement)
        final var finalDmResponse = dmResponse;
        final var finalGroundTruth = groundTruth;
        final var finalPlayerMessage = playerMessage;

        // 5. Fork Branch A (drift eval) and Branch B (extraction) concurrently.
        // AtomicReferences hold each branch's result; lambdas return Void so the
        // scope can use a single homogeneous Joiner<Void, Void>.
        // Parallel execution: Branch A performs drift evaluation against ground truth;
        // Branch B runs DICE extraction and anchor promotion. Both execute concurrently
        // via StructuredTaskScope; if either fails, the sibling is cancelled and the exception propagates.
        final List<EvalVerdict> verdicts;
        final ExtractionResult extractionResult;

        var verdictsRef = new AtomicReference<List<EvalVerdict>>(List.of());
        var extractionRef = new AtomicReference<>(ExtractionResult.empty());

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Void>awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withTimeout(PARALLEL_BRANCH_TIMEOUT))) {

            // Branch A: drift evaluation — only on turns that require it
            scope.fork(() -> {
                if (shouldEvaluate(turnType, finalGroundTruth)) {
                    verdictsRef.set(evaluateDrift(finalDmResponse, finalGroundTruth, finalPlayerMessage));
                }
                return null;
            });

            // Branch B: DICE extraction + promotion — only when enabled.
            // SimulationExtractionService is stateless (no shared mutable fields);
            // all state is passed as parameters or created locally per call.
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
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Post-response parallel branch failed", cause != null ? cause : e);
        } catch (StructuredTaskScope.TimeoutException e) {
            throw new RuntimeException("Post-response parallel branches timed out after "
                    + PARALLEL_BRANCH_TIMEOUT.toMinutes() + " minutes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Turn executor interrupted during parallel branches", e);
        }

        // Log branch results
        if (extractionEnabled) {
            currentSpan.setAttribute("sim.propositions_extracted", extractionResult.extractedCount());
            currentSpan.setAttribute("sim.propositions_promoted", extractionResult.promotedCount());
            logger.info("Turn {} extraction: {} extracted, {} promoted",
                    turnNumber, extractionResult.extractedCount(), extractionResult.promotedCount());
        }
        logger.info("Turn {} [{}]: player='{}', dm='{}', anchors={}, verdicts={}",
                turnNumber, turnType,
                truncate(playerMessage, 50), truncate(dmResponse, 50),
                anchors.size(), verdicts.size());

        // Build initial turn with verdicts from Branch A
        var turn = new SimulationTurn(
                turnNumber, playerMessage, dmResponse,
                turnType, attackStrategies, initialTrace, verdicts, List.of());

        // 6. Post-join sequential operations: reinforce, dormancy, state diff, compaction
        return buildResult(
                contextId, turnNumber, playerMessage, turn, extractionResult,
                injectionEnabled, previousAnchorState, compactionProvider,
                compactionConfig, dormancyConfig, mutableDormancyState);
    }

    // -------------------------------------------------------------------------
    // Sequential execution path (parallelPostResponse=false)
    // -------------------------------------------------------------------------

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
            Map<String, Anchor> previousAnchorState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            boolean extractionEnabled,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {

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
            logger.info("Turn {} extraction: {} extracted, {} promoted",
                    turnNumber, extractionResult.extractedCount(), extractionResult.promotedCount());
        }

        return buildResult(
                contextId, turnNumber, playerMessage, turn, extractionResult,
                injectionEnabled, previousAnchorState, compactionProvider,
                compactionConfig, dormancyConfig, mutableDormancyState);
    }

    // -------------------------------------------------------------------------
    // Shared post-response sequential operations
    // -------------------------------------------------------------------------

    /**
     * Applies reinforce, dormancy lifecycle, state diff, and optional compaction
     * after both parallel branches (or sequential extraction) have completed.
     * This method is shared by both the parallel and sequential execution paths.
     *
     * @param turn             the turn record produced by the DM call (verdicts already set)
     * @param extractionResult proposition extraction result (empty if extraction was disabled)
     */
    private TurnExecutionResult buildResult(
            String contextId,
            int turnNumber,
            String playerMessage,
            SimulationTurn turn,
            ExtractionResult extractionResult,
            boolean injectionEnabled,
            Map<String, Anchor> previousAnchorState,
            CompactedContextProvider compactionProvider,
            SimulationScenario.CompactionConfig compactionConfig,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> mutableDormancyState) {

        // Reinforce anchors that were actually injected for this turn.
        var reinforcedAnchorIds = new HashSet<String>();
        if (injectionEnabled && originalTraceHasAnchors(turn.contextTrace())) {
            for (var injectedAnchor : turn.contextTrace().injectedAnchors()) {
                anchorEngine.reinforce(injectedAnchor.id());
                reinforcedAnchorIds.add(injectedAnchor.id());
            }
        }

        // Refresh active anchors after extraction/promotion/reinforcement.
        var currentAnchors = anchorEngine.inject(contextId);
        var dormancyLifecycleEnabled = dormancyConfig != null
                                       && dormancyConfig.decayRate() > 0
                                       && dormancyConfig.dormancyTurns() > 0;
        var dormancyArchivedAnchorIds = applyDormancyLifecycle(
                currentAnchors,
                previousAnchorState,
                reinforcedAnchorIds,
                dormancyConfig,
                mutableDormancyState);

        if (dormancyLifecycleEnabled) {
            currentAnchors = anchorEngine.inject(contextId);
        }
        pruneDormancyState(mutableDormancyState, currentAnchors);

        // Update context trace with extraction metadata (8.7: merge both branch results)
        var originalTrace = turn.contextTrace();
        var enrichedTrace = new ContextTrace(
                originalTrace.turnNumber(), originalTrace.anchorTokens(), originalTrace.totalTokens(),
                currentAnchors, originalTrace.injectionEnabled(),
                originalTrace.assembledPrompt(), originalTrace.fullSystemPrompt(),
                originalTrace.fullUserPrompt(),
                originalTrace.budgetApplied(), originalTrace.anchorsExcluded(),
                extractionResult.extractedCount(), extractionResult.promotedCount(),
                extractionResult.extractedTexts(),
                originalTrace.hotCount(), originalTrace.warmCount(), originalTrace.coldCount());
        turn = new SimulationTurn(
                turn.turnNumber(), turn.playerMessage(), turn.dmResponse(),
                turn.turnType(), turn.attackStrategies(), enrichedTrace,
                turn.verdicts(), turn.anchorEvents());

        // Diff anchor state to produce events
        var anchorEvents = diffAnchorState(previousAnchorState, currentAnchors, turnNumber, dormancyArchivedAnchorIds);

        var enrichedTurn = new SimulationTurn(
                turn.turnNumber(), turn.playerMessage(), turn.dmResponse(),
                turn.turnType(), turn.attackStrategies(), turn.contextTrace(),
                turn.verdicts(), anchorEvents);

        // Build current state map for next turn
        var currentState = new HashMap<String, Anchor>();
        for (var anchor : currentAnchors) {
            currentState.put(anchor.id(), anchor);
        }

        // Compaction handling
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

    /**
     * Diff previous and current anchor state to detect lifecycle events.
     *
     * @param previous   anchor state from end of previous turn (keyed by id)
     * @param current    current active anchors
     * @param turnNumber 1-based turn counter for event attribution
     * @param archivedAnchorIds anchor IDs archived this turn via dormancy lifecycle
     *
     * @return list of detected anchor events
     */
    static List<SimulationTurn.AnchorEvent> diffAnchorState(
            Map<String, Anchor> previous,
            List<Anchor> current,
            int turnNumber,
            Set<String> archivedAnchorIds) {

        var events = new ArrayList<SimulationTurn.AnchorEvent>();
        var currentById = new HashMap<String, Anchor>();
        for (var anchor : current) {
            currentById.put(anchor.id(), anchor);
        }

        // Detect CREATED, rank transitions, and authority upgrades.
        for (var anchor : current) {
            var prev = previous.get(anchor.id());
            if (prev == null) {
                    events.add(new SimulationTurn.AnchorEvent(
                        turnNumber, "CREATED", anchor.id(), anchor.text(),
                        anchor.authority().name(), anchor.rank(), 0, "sim_extraction"));
            } else {
                if (prev.rank() != anchor.rank()) {
                    var increased = anchor.rank() > prev.rank();
                    events.add(new SimulationTurn.AnchorEvent(
                            turnNumber,
                            increased ? "REINFORCED" : "DECAYED",
                            anchor.id(),
                            anchor.text(),
                            anchor.authority().name(),
                            anchor.rank(),
                            prev.rank(),
                            increased ? "reinforcement" : "dormancy_decay"));
                }
                if (prev.authority() != anchor.authority()) {
                    events.add(new SimulationTurn.AnchorEvent(
                            turnNumber, "AUTHORITY_CHANGED", anchor.id(), anchor.text(),
                            anchor.authority().name(), anchor.rank(), prev.rank(), "authority_upgrade"));
                }
            }
        }

        // Detect archived/evicted anchors (present previously, missing now).
        for (var entry : previous.entrySet()) {
            if (!currentById.containsKey(entry.getKey())) {
                var evicted = entry.getValue();
                var wasArchived = archivedAnchorIds.contains(entry.getKey());
                events.add(new SimulationTurn.AnchorEvent(
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
            List<Anchor> currentAnchors,
            Map<String, Anchor> previousAnchorState,
            Set<String> reinforcedAnchorIds,
            SimulationScenario.DormancyConfig dormancyConfig,
            Map<String, Integer> dormancyState) {
        if (dormancyConfig == null
            || dormancyConfig.decayRate() <= 0
            || dormancyConfig.dormancyTurns() <= 0
            || currentAnchors.isEmpty()) {
            return Set.of();
        }

        var archivedIds = new HashSet<String>();
        var decayRate = clamp01(dormancyConfig.decayRate());
        var archiveThreshold = archiveThreshold(dormancyConfig.revivalThreshold());

        for (var anchor : currentAnchors) {
            if (anchor.pinned()) {
                dormancyState.put(anchor.id(), 0);
                continue;
            }

            var previous = previousAnchorState.get(anchor.id());
            if (previous == null || reinforcedAnchorIds.contains(anchor.id())) {
                dormancyState.put(anchor.id(), 0);
                continue;
            }

            var dormantTurns = dormancyState.getOrDefault(anchor.id(), 0) + 1;
            dormancyState.put(anchor.id(), dormantTurns);
            if (dormantTurns < dormancyConfig.dormancyTurns()) {
                continue;
            }

            var tierMultiplier = tierMultiplierFor(anchor.memoryTier());
            var decayedRank = decayedRank(anchor.rank(), decayRate, tierMultiplier);
            if (decayedRank <= archiveThreshold) {
                anchorEngine.archive(anchor.id(), ArchiveReason.DORMANCY_DECAY);
                archivedIds.add(anchor.id());
                dormancyState.remove(anchor.id());
            } else if (decayedRank < anchor.rank()) {
                anchorEngine.applyDecay(anchor.id(), decayedRank);
            }
        }
        return archivedIds;
    }

    private void pruneDormancyState(Map<String, Integer> dormancyState, List<Anchor> currentAnchors) {
        if (dormancyState.isEmpty()) {
            return;
        }
        var activeIds = currentAnchors.stream().map(Anchor::id).collect(java.util.stream.Collectors.toSet());
        dormancyState.keySet().removeIf(id -> !activeIds.contains(id));
    }

    private int decayedRank(int currentRank, double decayRate, double tierMultiplier) {
        var effectiveRate = decayRate / Math.max(tierMultiplier, 0.01);
        var reduction = Math.max(1, (int) Math.round(currentRank * effectiveRate));
        return Anchor.clampRank(currentRank - reduction);
    }

    private double tierMultiplierFor(MemoryTier tier) {
        var tierConfig = anchorConfig.tier();
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
        return Anchor.MIN_RANK + (int) Math.round((Anchor.MAX_RANK - Anchor.MIN_RANK) * clamped);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Convert scenario compaction config to assembly compaction config.
     */
    private static dev.dunnam.diceanchors.assembly.CompactionConfig toAssemblyConfig(
            SimulationScenario.CompactionConfig scenarioConfig) {
        return new dev.dunnam.diceanchors.assembly.CompactionConfig(
                scenarioConfig.enabled(),
                scenarioConfig.tokenThreshold(),
                scenarioConfig.messageThreshold(),
                scenarioConfig.forceAtTurns() != null ? scenarioConfig.forceAtTurns() : List.of(),
                scenarioConfig.minMatchRatio() != null ? scenarioConfig.minMatchRatio() : 0.5,
                scenarioConfig.maxRetries() != null ? scenarioConfig.maxRetries() : 2,
                scenarioConfig.retryBackoffMillis() != null ? scenarioConfig.retryBackoffMillis() : 1000L,
                scenarioConfig.eventsEnabled() != null ? scenarioConfig.eventsEnabled() : true);
    }

    private String buildSystemPrompt(String setting, String anchorBlock, String propositionBlock) {
        return PromptTemplates.render(PromptPathConstants.SIM_SYSTEM, Map.of(
                "setting", setting != null ? setting : "",
                "anchor_block", anchorBlock != null ? anchorBlock : "",
                "proposition_block", propositionBlock != null ? propositionBlock : ""));
    }

    private String buildUserPrompt(String playerMessage, List<String> history) {
        var recentHistory = List.<String>of();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 10);
            recentHistory = history.subList(start, history.size());
        }
        return PromptTemplates.render(PromptPathConstants.SIM_USER, Map.of(
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
            String playerMessage) {
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
        var userPrompt = PromptTemplates.render(PromptPathConstants.SIM_DRIFT_EVALUATION_USER, templateVars);

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
        return PromptTemplates.render(PromptPathConstants.SIM_CONVERSATION_LINE, Map.of(
                "role", role != null ? role : "",
                "text", text != null ? text : "")).stripTrailing();
    }

    private boolean originalTraceHasAnchors(ContextTrace trace) {
        return trace != null && trace.injectedAnchors() != null && !trace.injectedAnchors().isEmpty();
    }

    private String combineInjectedBlocks(String anchorBlock, String propositionBlock) {
        var parts = new ArrayList<String>();
        if (anchorBlock != null && !anchorBlock.isBlank()) {
            parts.add(anchorBlock.strip());
        }
        if (propositionBlock != null && !propositionBlock.isBlank()) {
            parts.add(propositionBlock.strip());
        }
        return String.join("\n\n", parts);
    }
}
