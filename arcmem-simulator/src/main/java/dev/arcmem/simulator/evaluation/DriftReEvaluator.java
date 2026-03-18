package dev.arcmem.simulator.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.prompt.PromptTemplates;
import dev.arcmem.simulator.engine.ChatModelHolder;
import dev.arcmem.simulator.engine.DriftEvaluationResult;
import dev.arcmem.simulator.engine.EvalVerdict;
import dev.arcmem.simulator.prompt.SimulationPromptPaths;
import dev.arcmem.simulator.scenario.SimulationScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DriftReEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(DriftReEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");

    private final ChatModelHolder chatModel;

    public DriftReEvaluator(ChatModelHolder chatModel) {
        this.chatModel = chatModel;
    }

    public List<EvalVerdict> evaluate(
            String dmResponse,
            List<SimulationScenario.GroundTruth> groundTruth,
            String playerMessage,
            List<MemoryUnit> injectedUnits,
            JudgeConfig judgeConfig) {

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

        var systemPrompt = PromptTemplates.load(judgeConfig.systemPromptPath());
        var userPrompt = PromptTemplates.render(SimulationPromptPaths.DRIFT_EVALUATION_USER, templateVars);

        try {
            var evalResponse = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt))));
            var raw = evalResponse.getResult().getOutput().getText();
            var json = stripCodeFences(raw);
            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            return result.toEvalVerdicts(judgeConfig.confidenceThreshold());
        } catch (Exception e) {
            logger.warn("Drift evaluation failed: {}", e.getMessage());
            return List.of();
        }
    }

    public static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        var trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = CODE_FENCE.matcher(trimmed).replaceAll("");
        }
        return trimmed.strip();
    }

    public List<EvalVerdict> parseVerdictsJson(
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

    List<EvalVerdict> fallbackParseVerdicts(
            String raw,
            List<SimulationScenario.GroundTruth> groundTruth) {
        var verdicts = new ArrayList<EvalVerdict>();
        var lower = raw.toLowerCase();

        for (var fact : groundTruth) {
            var factId = fact.id();
            var factLower = factId.toLowerCase();

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
}
