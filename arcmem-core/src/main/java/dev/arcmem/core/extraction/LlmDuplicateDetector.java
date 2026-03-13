package dev.arcmem.core.extraction;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM-based duplicate detection. Calls the chat model to determine semantic
 * equivalence between a candidate and existing {@link MemoryUnit}.
 *
 * <p>Fail-open: any LLM or parse failure returns {@code false} (assume unique).
 */
public class LlmDuplicateDetector implements DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(LlmDuplicateDetector.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final LlmCallService llmCallService;

    public LlmDuplicateDetector(ChatModel chatModel, LlmCallService llmCallService) {
        this.chatModel = chatModel;
        this.llmCallService = llmCallService;
    }

    @Override
    public boolean isDuplicate(String candidateText, List<MemoryUnit> existingUnits) {
        if (existingUnits.isEmpty()) {
            return false;
        }
        var existingFacts = existingUnits.stream().map(MemoryUnit::text).toList();
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
            logger.warn("LLM duplicate detection failed, assuming unique (fail-open): {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Boolean> batchIsDuplicate(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        if (candidateTexts.isEmpty()) {
            return Map.of();
        }
        if (existingUnits.isEmpty()) {
            return candidateTexts.stream().collect(Collectors.toMap(c -> c, c -> false));
        }
        try {
            return batchLlmCheck(candidateTexts, existingUnits);
        } catch (Exception e) {
            logger.warn("Batch LLM duplicate check failed, falling back to per-candidate: {}", e.getMessage());
            var result = new LinkedHashMap<String, Boolean>();
            for (var candidate : candidateTexts) {
                result.put(candidate, isDuplicate(candidate, existingUnits));
            }
            return result;
        }
    }

    private Map<String, Boolean> batchLlmCheck(List<String> candidates, List<MemoryUnit> units) {
        var existingFacts = units.stream().map(MemoryUnit::text).toList();
        var systemPrompt = PromptTemplates.load(PromptPathConstants.DICE_BATCH_DUPLICATE_SYSTEM);
        var userPrompt = PromptTemplates.render(PromptPathConstants.DICE_BATCH_DUPLICATE_USER, Map.of(
                "existing_facts", existingFacts,
                "candidates", candidates));
        var responseText = llmCallService.callBatched(systemPrompt, userPrompt);
        return parseBatchResponse(responseText, candidates);
    }

    private Map<String, Boolean> parseBatchResponse(String responseText, List<String> candidates) {
        try {
            var json = responseText.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
            var parsed = OBJECT_MAPPER.readValue(json, BatchDedupResult.class);
            var result = new LinkedHashMap<String, Boolean>();
            for (var entry : parsed.results()) {
                result.put(entry.candidate(), entry.isDuplicate());
            }
            // Fail-open: omitted candidates assumed unique
            for (var candidate : candidates) {
                result.putIfAbsent(candidate, false);
            }
            return result;
        } catch (Exception e) {
            logger.warn("Failed to parse batch dedup response, assuming all unique (fail-open): {}", e.getMessage());
            return candidates.stream().collect(Collectors.toMap(c -> c, c -> false));
        }
    }
}
