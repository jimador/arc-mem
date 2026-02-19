package dev.dunnam.diceanchors.sim.engine.adversary;

import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.ChatModelHolder;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.StrategyCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates in-character player dialogue from an {@link AttackPlan} using a single LLM call.
 * <p>
 * Invariant I5: exactly one {@code ChatModel} call per {@link #generateMessage} invocation.
 */
public class AdaptiveAttackPrompter {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveAttackPrompter.class);

    private final ChatModelHolder chatModel;
    private final StrategyCatalog catalog;

    public AdaptiveAttackPrompter(ChatModelHolder chatModel, StrategyCatalog catalog) {
        this.chatModel = chatModel;
        this.catalog = catalog;
    }

    /**
     * Generate an in-character player message that embeds the attack in natural dialogue.
     *
     * @param plan                the adversary's attack plan for this turn
     * @param persona             the player persona to voice
     * @param conversationHistory prior turn lines formatted as "Role: text"
     * @return the generated player message text
     */
    public String generateMessage(
            AttackPlan plan,
            SimulationScenario.PersonaConfig persona,
            List<String> conversationHistory) {

        var systemPrompt = buildSystemPrompt(plan, persona);
        var userPrompt = buildUserPrompt(plan, conversationHistory);

        logger.debug("AdaptiveAttackPrompter: calling ChatModel for plan tier={}, targets={}",
                plan.tier(), plan.targetFacts().size());

        try {
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt))));
            return response.getResult().getOutput().getText().trim();
        } catch (Exception e) {
            logger.warn("AdaptiveAttackPrompter: ChatModel call failed — {}", e.getMessage());
            return fallbackMessage(plan, persona);
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction (Tasks 3.3–3.4)
    // -------------------------------------------------------------------------

    /** Task 3.3: Build system prompt from persona + attack rationale. */
    private String buildSystemPrompt(AttackPlan plan, SimulationScenario.PersonaConfig persona) {
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
        sb.append("\n");
        sb.append("Your approach this turn: ").append(plan.rationale()).append("\n");
        if (plan.sequence() != null) {
            sb.append("Sequence phase: ").append(plan.sequence().phase()).append("\n");
        }
        sb.append("\n");
        sb.append("Stay in character. Write only your character's dialogue — no narration, no quotes.");
        return sb.toString();
    }

    /** Task 3.4: Build user prompt referencing target facts and strategy display names. */
    private String buildUserPrompt(AttackPlan plan, List<String> conversationHistory) {
        var sb = new StringBuilder();

        if (!conversationHistory.isEmpty()) {
            var recentHistory = conversationHistory.stream()
                    .skip(Math.max(0, conversationHistory.size() - 6))
                    .collect(Collectors.joining("\n"));
            sb.append("Recent conversation:\n").append(recentHistory).append("\n\n");
        }

        sb.append("Challenge or undermine the following facts in your next statement:\n");
        for (var fact : plan.targetFacts()) {
            sb.append("- ").append(fact).append("\n");
        }

        if (!plan.strategies().isEmpty()) {
            sb.append("\nApply these techniques naturally:\n");
            for (var strategy : plan.strategies()) {
                var displayName = catalog.findById(strategy.name())
                        .map(d -> d.displayName() + " — " + d.promptGuidance())
                        .orElse(strategy.name());
                sb.append("- ").append(displayName).append("\n");
            }
        }

        sb.append("\nWrite your character's dialogue now:");
        return sb.toString();
    }

    private String fallbackMessage(AttackPlan plan, SimulationScenario.PersonaConfig persona) {
        var target = plan.targetFacts().isEmpty() ? "that fact" : plan.targetFacts().get(0);
        return "I'm not sure that's right about " + target + ". Can you explain that again?";
    }
}
