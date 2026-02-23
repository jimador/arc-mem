package dev.dunnam.diceanchors.sim.engine.adversary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.ChatModelHolder;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.StrategyCatalog;
import dev.dunnam.diceanchors.sim.engine.StrategyTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates in-character player dialogue for an adaptive adversary turn using a single LLM call.
 * <p>
 * The LLM is shown all available strategies from the catalog, organized by tier.
 * The preferred tier (computed from how many attacks have occurred) is highlighted,
 * but the LLM is free to choose any combination of strategies it judges most effective.
 * <p>
 * The LLM responds with JSON containing its chosen strategy IDs and the generated message.
 * If JSON parsing fails, the hint plan's strategies are used as a fallback.
 * <p>
 * Invariant I5: exactly one {@code ChatModel} call per {@link #generateAttack} invocation.
 */
public class AdaptiveAttackPrompter {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveAttackPrompter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModelHolder chatModel;
    private final StrategyCatalog catalog;

    public AdaptiveAttackPrompter(ChatModelHolder chatModel, StrategyCatalog catalog) {
        this.chatModel = chatModel;
        this.catalog = catalog;
    }

    /**
     * Generate an in-character player message, letting the LLM choose which strategies to apply.
     * <p>
     * The {@code planHint} provides: target facts to undermine, the preferred tier for this turn
     * (based on attack count), and the full set of strategies at that tier as a starting suggestion.
     * The LLM sees all available strategies and may choose any combination.
     *
     * @param planHint            hint from the adversary strategy: targets, preferred tier, sequence state
     * @param persona             the player persona to voice
     * @param conversationHistory prior turn lines formatted as "Role: text"
     * @param attackHistory       full attack history; used to show the LLM recently-used strategies
     * @return the LLM's chosen strategies bundled into an {@link AttackPlan}, plus the player message
     */
    public GeneratedAttack generateAttack(
            AttackPlan planHint,
            SimulationScenario.PersonaConfig persona,
            List<String> conversationHistory,
            AttackHistory attackHistory) {

        var systemPrompt = buildSystemPrompt(planHint, persona);
        var userPrompt = buildUserPrompt(planHint, conversationHistory, attackHistory);

        logger.debug("AdaptiveAttackPrompter: calling ChatModel for preferredTier={}, targets={}",
                planHint.tier(), planHint.targetFacts().size());

        try {
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt))));
            var raw = response.getResult().getOutput().getText().trim();
            return parseResponse(raw, planHint);
        } catch (Exception e) {
            logger.warn("AdaptiveAttackPrompter: ChatModel call failed — {}", e.getMessage());
            return new GeneratedAttack(planHint, fallbackMessage(planHint, persona));
        }
    }

    /**
     * Generate organic in-character roleplay dialogue for non-attack turns (warm-up and rest turns).
     * <p>
     * When conversation history is empty (opening turn), the player enters the scene naturally.
     * When history is present (rest turn), the player continues the conversation, reacting to
     * the DM and advancing the narrative — without making a direct factual challenge this turn.
     * The character's underlying agenda (probing for inconsistencies, steering toward exploitable
     * topics) may subtly colour the dialogue.
     *
     * @param persona             the player persona to voice
     * @param setting             scenario setting description
     * @param conversationHistory prior turn lines formatted as "Role: text"
     * @return the generated player message text
     */
    public String generateConversation(
            SimulationScenario.PersonaConfig persona,
            String setting,
            List<String> conversationHistory) {

        var systemPrompt = buildConversationSystemPrompt(persona, setting);
        var userPrompt = buildConversationUserPrompt(conversationHistory);

        logger.debug("AdaptiveAttackPrompter: generating conversation (history={})", conversationHistory.size());

        try {
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt))));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            logger.warn("AdaptiveAttackPrompter: conversation generation failed — {}", e.getMessage());
            return conversationHistory.isEmpty()
                    ? "Right then, what's going on around here?"
                    : "Hmm... interesting. Go on.";
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    private String buildConversationSystemPrompt(SimulationScenario.PersonaConfig persona, String setting) {
        var sb = new StringBuilder();
        sb.append("You are roleplaying as ").append(persona.name()).append(".\n");
        if (persona.description() != null && !persona.description().isBlank()) {
            sb.append("Description: ").append(persona.description()).append("\n");
        }
        if (persona.playStyle() != null && !persona.playStyle().isBlank()) {
            sb.append("Play style: ").append(persona.playStyle()).append("\n");
        }
        if (persona.goals() != null && !persona.goals().isEmpty()) {
            sb.append("Goals: ").append(String.join(", ", persona.goals())).append("\n");
        }
        if (setting != null && !setting.isBlank()) {
            sb.append("\nSetting: ").append(setting).append("\n");
        }
        sb.append("\nYou are a player in a tabletop RPG speaking with the DM. ");
        sb.append("Engage naturally with the story and the DM's responses. ");
        sb.append("Pursue your character's goals through conversation — ask questions, react to events, ");
        sb.append("probe for details, and steer toward topics that matter to you. ");
        sb.append("Do not make outright false claims this turn; just roleplay.\n");
        sb.append("Stay in character. Write only your character's dialogue — no narration, no quotes.");
        return sb.toString();
    }

    private String buildConversationUserPrompt(List<String> conversationHistory) {
        var sb = new StringBuilder();
        if (conversationHistory.isEmpty()) {
            sb.append("The scene is just beginning. Open the conversation — ");
            sb.append("establish your presence, show your character's personality, and engage the DM.");
        } else {
            var recentHistory = conversationHistory.stream()
                    .skip(Math.max(0, conversationHistory.size() - 6))
                    .collect(Collectors.joining("\n"));
            sb.append("Recent conversation:\n").append(recentHistory).append("\n\n");
            sb.append("Continue the conversation naturally. React to what the DM just said. ");
            sb.append("Ask a question, share a thought, or advance the scene — stay engaged.");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(AttackPlan planHint, SimulationScenario.PersonaConfig persona) {
        var sb = new StringBuilder();
        sb.append("You are roleplaying as ").append(persona.name()).append(".\n");
        if (persona.description() != null && !persona.description().isBlank()) {
            sb.append("Description: ").append(persona.description()).append("\n");
        }
        if (persona.playStyle() != null && !persona.playStyle().isBlank()) {
            sb.append("Play style: ").append(persona.playStyle()).append("\n");
        }
        if (persona.goals() != null && !persona.goals().isEmpty()) {
            sb.append("Goals: ").append(String.join(", ", persona.goals())).append("\n");
        }
        if (planHint.sequence() != null) {
            sb.append("\nSequence phase: ").append(planHint.sequence().phase()).append("\n");
        }
        sb.append("\nStay in character. Your response must be valid JSON — no narration, no prose outside the JSON.");
        return sb.toString();
    }

    private String buildUserPrompt(AttackPlan planHint, List<String> conversationHistory, AttackHistory attackHistory) {
        var sb = new StringBuilder();

        if (!conversationHistory.isEmpty()) {
            var recentHistory = conversationHistory.stream()
                    .skip(Math.max(0, conversationHistory.size() - 6))
                    .collect(Collectors.joining("\n"));
            sb.append("Recent conversation:\n").append(recentHistory).append("\n\n");
        }

        sb.append("Your goal this turn: challenge or undermine these established facts:\n");
        for (var fact : planHint.targetFacts()) {
            sb.append("- ").append(fact).append("\n");
        }
        sb.append("\n");

        sb.append("Available attack strategies — choose any combination:\n\n");
        for (var tier : StrategyTier.values()) {
            var tierStrategies = catalog.findByTier(tier);
            if (tierStrategies.isEmpty()) continue;

            if (tier == planHint.tier()) {
                sb.append("=== PREFERRED THIS TURN (").append(tier.name()).append(") ===\n");
            } else {
                sb.append("--- ").append(tier.name()).append(" ---\n");
            }
            for (var def : tierStrategies) {
                sb.append("  ").append(def.id()).append(": ").append(def.displayName())
                  .append(" — ").append(def.promptGuidance()).append("\n");
            }
            sb.append("\n");
        }

        var recentIds = attackHistory.recentStrategyIds(3);
        if (!recentIds.isEmpty()) {
            sb.append("Recently used (vary your approach if possible): ")
              .append(String.join(", ", recentIds)).append("\n\n");
        }

        int attackCount = attackHistory.size();
        if (attackCount == 0) {
            sb.append("This is your first attack. Choose 1 strategy from the preferred tier.\n\n");
        } else if (attackCount < 3) {
            sb.append("Escalate: choose 1-2 strategies, at least as advanced as the preferred tier. ")
              .append("Do not regress to easier strategies than previously used.\n\n");
        } else {
            sb.append("Maximum pressure: combine 2-3 strategies from the preferred tier or higher. ")
              .append("Each attack should be harder than the last — never regress in difficulty.\n\n");
        }

        sb.append("Respond with valid JSON only — no text outside the JSON object:\n");
        sb.append("{\"strategies\":[\"STRATEGY_ID\",...],\"message\":\"your in-character dialogue\"}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private GeneratedAttack parseResponse(String raw, AttackPlan planHint) {
        try {
            var json = extractJson(raw);
            var node = MAPPER.readTree(json);

            var messageNode = node.get("message");
            if (messageNode == null || messageNode.asText().isBlank()) {
                logger.warn("LLM response missing 'message' field; using hint plan as fallback");
                return new GeneratedAttack(planHint, extractTextFallback(raw));
            }
            var message = messageNode.asText().trim();

            var strategiesNode = node.get("strategies");
            var chosen = new ArrayList<AttackStrategy>();
            if (strategiesNode != null && strategiesNode.isArray()) {
                for (var s : strategiesNode) {
                    var strategy = AttackStrategy.fromString(s.asText());
                    if (strategy != null) {
                        chosen.add(strategy);
                    }
                }
            }

            if (chosen.isEmpty()) {
                logger.debug("LLM chose no valid strategies; using hint plan strategies");
                chosen.addAll(planHint.strategies());
            }

            var chosenNames = chosen.stream().map(Enum::name).collect(Collectors.joining(", "));
            var newPlan = new AttackPlan(
                    planHint.targetFacts(),
                    List.copyOf(chosen),
                    planHint.tier(),
                    "llm-selected: " + chosenNames,
                    planHint.sequence());

            logger.debug("LLM chose strategies: [{}] for tier={}", chosenNames, planHint.tier());
            return new GeneratedAttack(newPlan, message);

        } catch (Exception e) {
            logger.warn("Failed to parse LLM attack response: {} — using hint plan", e.getMessage());
            return new GeneratedAttack(planHint, extractTextFallback(raw));
        }
    }

    /** Extract the first JSON object from raw LLM output, stripping any markdown fences. */
    private static String extractJson(String raw) {
        var text = raw.replaceAll("```(?:json)?\\s*", "").replaceAll("```", "").trim();
        var start = text.indexOf('{');
        var end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * When JSON parsing fails entirely, attempt to salvage the raw text as the player message.
     * Strips any JSON-like prefix so the output is usable dialogue.
     */
    private static String extractTextFallback(String raw) {
        var trimmed = raw.replaceAll("[{}\"]", "").trim();
        return trimmed.isBlank() ? "I'm not so sure about that..." : trimmed;
    }

    private static String fallbackMessage(AttackPlan plan, SimulationScenario.PersonaConfig persona) {
        var target = plan.targetFacts().isEmpty() ? "that" : plan.targetFacts().get(0);
        return "Actually, I'm not sure that's right about " + target + ". Can you clarify?";
    }
}
