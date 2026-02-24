package dev.dunnam.diceanchors.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composite duplicate detector using configurable strategy.
 * Supports fast normalized-string matching, LLM-based detection, or both.
 * <p>
 * Strategy is controlled via {@code dice-anchors.anchor.dedup-strategy}:
 * <ul>
 *   <li>FAST_ONLY — normalized-string only, no LLM calls</li>
 *   <li>LLM_ONLY — LLM only, skips fast-path</li>
 *   <li>FAST_THEN_LLM — fast-path first, LLM fallback (default)</li>
 * </ul>
 */
@Service
public class DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final AnchorEngine engine;
    private final NormalizedStringDuplicateDetector fastDetector;
    private final DuplicateDetectionStrategy strategy;
    private final LlmCallService llmCallService;

    public DuplicateDetector(ChatModel chatModel, AnchorEngine engine,
                             NormalizedStringDuplicateDetector fastDetector,
                             DiceAnchorsProperties properties,
                             LlmCallService llmCallService) {
        this.chatModel = chatModel;
        this.engine = engine;
        this.fastDetector = fastDetector;
        this.strategy = DuplicateDetectionStrategy.valueOf(properties.anchor().dedupStrategy());
        this.llmCallService = llmCallService;
        logger.info("Duplicate detection strategy: {}", this.strategy);
    }

    public boolean isDuplicate(String contextId, String candidateText) {
        var anchors = engine.inject(contextId);
        if (anchors.isEmpty()) {
            return false;
        }

        if (strategy != DuplicateDetectionStrategy.LLM_ONLY) {
            if (fastDetector.isDuplicate(candidateText, anchors)) {
                logger.info("Fast-path duplicate detected: '{}'", candidateText);
                return true;
            }
            if (strategy == DuplicateDetectionStrategy.FAST_ONLY) {
                return false;
            }
        }

        return llmDuplicateCheck(candidateText, anchors);
    }

    /**
     * Evaluate multiple candidates for duplicate status in a single LLM call.
     * Fast-path candidates (normalized-string match) are resolved without LLM.
     * Remaining candidates are batched into a single LLM call.
     * Falls back to individual isDuplicate() calls if the batch LLM call fails.
     *
     * @param candidateTexts list of candidate proposition texts
     * @return map from candidate text to duplicate status (true = is duplicate)
     */
    public Map<String, Boolean> batchIsDuplicate(String contextId, List<String> candidateTexts) {
        if (candidateTexts.isEmpty()) {
            return Map.of();
        }
        var anchors = engine.inject(contextId);
        if (anchors.isEmpty()) {
            return candidateTexts.stream().collect(Collectors.toMap(c -> c, c -> false));
        }

        var result = new LinkedHashMap<String, Boolean>();
        var llmBatch = new ArrayList<String>();

        for (var candidate : candidateTexts) {
            if (strategy != DuplicateDetectionStrategy.LLM_ONLY
                    && fastDetector.isDuplicate(candidate, anchors)) {
                logger.info("Fast-path batch duplicate: '{}'", candidate);
                result.put(candidate, true);
            } else if (strategy == DuplicateDetectionStrategy.FAST_ONLY) {
                result.put(candidate, false);
            } else {
                llmBatch.add(candidate);
            }
        }

        if (!llmBatch.isEmpty()) {
            try {
                var batchResults = batchLlmDuplicateCheck(llmBatch, anchors);
                result.putAll(batchResults);
            } catch (Exception e) {
                logger.warn("Batch LLM duplicate check failed, falling back to per-candidate: {}", e.getMessage());
                for (var candidate : llmBatch) {
                    result.put(candidate, llmDuplicateCheck(candidate, anchors));
                }
            }
        }
        return result;
    }

    private Map<String, Boolean> batchLlmDuplicateCheck(List<String> candidates, List<Anchor> anchors) {
        var existingFacts = anchors.stream().map(Anchor::text).toList();
        var systemPrompt = PromptTemplates.load(PromptPathConstants.DICE_BATCH_DUPLICATE_SYSTEM);
        var userPrompt = PromptTemplates.render(PromptPathConstants.DICE_BATCH_DUPLICATE_USER, Map.of(
                "existing_facts", existingFacts,
                "candidates", candidates));

        var responseText = llmCallService.callBatched(systemPrompt, userPrompt);
        return parseBatchDedupResponse(responseText, candidates);
    }

    private Map<String, Boolean> parseBatchDedupResponse(String responseText, List<String> candidates) {
        try {
            var json = responseText.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
            var parsed = OBJECT_MAPPER.readValue(json, BatchDedupResult.class);
            var result = new LinkedHashMap<String, Boolean>();
            for (var entry : parsed.results()) {
                result.put(entry.candidate(), entry.isDuplicate());
            }
            // Ensure all candidates have a result (LLM may have omitted some)
            for (var candidate : candidates) {
                result.putIfAbsent(candidate, false);
            }
            return result;
        } catch (Exception e) {
            logger.warn("Failed to parse batch dedup response, assuming all unique: {}", e.getMessage());
            return candidates.stream().collect(Collectors.toMap(c -> c, c -> false));
        }
    }

    private boolean llmDuplicateCheck(String candidateText, List<Anchor> anchors) {
        var existingFacts = anchors.stream().map(Anchor::text).toList();
        var systemPrompt = PromptTemplates.load(PromptPathConstants.DICE_DUPLICATE_SYSTEM);
        var userPrompt = PromptTemplates.render(PromptPathConstants.DICE_DUPLICATE_USER, Map.of(
                "existing_facts", existingFacts,
                "candidate_fact", candidateText));

        try {
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            )));
            var answer = response.getResult().getOutput().getText().trim().toUpperCase();
            var isDup = answer.contains("DUPLICATE");
            if (isDup) {
                logger.info("LLM duplicate detected: '{}'", candidateText);
            } else {
                logger.debug("LLM says unique: '{}'", candidateText);
            }
            return isDup;
        } catch (Exception e) {
            logger.warn("LLM duplicate detection failed, assuming unique: {}", e.getMessage());
            return false;
        }
    }
}
