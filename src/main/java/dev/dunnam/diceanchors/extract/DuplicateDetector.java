package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final ChatModel chatModel;
    private final AnchorEngine engine;
    private final NormalizedStringDuplicateDetector fastDetector;
    private final DuplicateDetectionStrategy strategy;

    public DuplicateDetector(ChatModel chatModel, AnchorEngine engine,
                             NormalizedStringDuplicateDetector fastDetector,
                             DiceAnchorsProperties properties) {
        this.chatModel = chatModel;
        this.engine = engine;
        this.fastDetector = fastDetector;
        this.strategy = DuplicateDetectionStrategy.valueOf(properties.anchor().dedupStrategy());
        logger.info("Duplicate detection strategy: {}", this.strategy);
    }

    /**
     * Check if the candidate text is a duplicate of any existing anchor.
     *
     * @return true if it IS a duplicate (should NOT be promoted)
     */
    public boolean isDuplicate(String contextId, String candidateText) {
        var anchors = engine.inject(contextId);
        if (anchors.isEmpty()) {
            return false;
        }

        // Fast-path: normalized string matching
        if (strategy != DuplicateDetectionStrategy.LLM_ONLY) {
            if (fastDetector.isDuplicate(candidateText, anchors)) {
                logger.info("Fast-path duplicate detected: '{}'", candidateText);
                return true;
            }
            if (strategy == DuplicateDetectionStrategy.FAST_ONLY) {
                return false;
            }
        }

        // LLM fallback
        return llmDuplicateCheck(candidateText, anchors);
    }

    private boolean llmDuplicateCheck(String candidateText, List<dev.dunnam.diceanchors.anchor.Anchor> anchors) {
        var existingFacts = anchors.stream().map(dev.dunnam.diceanchors.anchor.Anchor::text).toList();
        var systemPrompt = PromptTemplates.load("prompts/dice/duplicate-system.jinja");
        var userPrompt = PromptTemplates.render("prompts/dice/duplicate-user.jinja", java.util.Map.of(
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
