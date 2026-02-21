package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.DriftStrategyDefinition;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.StrategyCatalog;
import dev.dunnam.diceanchors.sim.engine.TurnType;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Standalone conversation panel showing turn-by-turn messages during simulation.
 * <p>
 * Renders player messages (blue/red border), DM responses (green with verdict-colored
 * left borders), system messages, injection state tags, and turn type badges.
 * Supports click-to-select turn highlighting.
 */
public class ConversationPanel extends VerticalLayout implements SimulationProgressListener {

    private static final StrategyCatalog STRATEGY_CATALOG =
            StrategyCatalog.loadFromClasspath("simulations/strategy-catalog.yml");

    private static final String[] THINKING_MESSAGES = {
            "DM is considering the situation...",
            "The DM ponders your words...",
            "Rolling behind the screen..."
    };

    private final Div thinkingIndicator;
    private final Span thinkingLabel;
    private Div selectedBubble;
    private Consumer<Integer> turnSelectionCallback;

    public ConversationPanel() {
        setPadding(true);
        setSpacing(false);
        setSizeFull();
        addClassName("ar-conversation");

        thinkingLabel = new Span(THINKING_MESSAGES[0]);
        thinkingLabel.addClassName("ar-thinking-label");
        thinkingIndicator = new Div(thinkingLabel);
        thinkingIndicator.addClassName("ar-thinking-indicator");

        showPlaceholder();
    }

    @Override
    public void onTurnStarted(SimulationProgress progress) {
        appendTurn(progress);
        thinkingLabel.setText(THINKING_MESSAGES[progress.turnNumber() % THINKING_MESSAGES.length]);
        add(thinkingIndicator);
    }

    @Override
    public void onTurnCompleted(SimulationProgress progress) {
        remove(thinkingIndicator);
        appendDmBubble(progress);
    }

    @Override
    public void onSimulationCompleted(SimulationProgress progress) {
        remove(thinkingIndicator);
        if (progress.phase() == SimulationProgress.SimulationPhase.CANCELLED) {
            appendSystemMessage("Simulation cancelled.");
        } else {
            appendSystemMessage("Simulation complete! Review the Drift Summary and Context Inspector tabs.");
        }
    }

    /**
     * Append a turn's messages (player + DM) from a simulation progress snapshot.
     */
    public void appendTurn(SimulationProgress progress) {
        if (progress.lastPlayerMessage() != null) {
            var bubble = createPlayerBubble(
                    progress.turnNumber(),
                    progress.lastPlayerMessage(),
                    isAttackTurn(progress),
                    progress.injectionState(),
                    progress.turnType(),
                    progress.attackStrategy());
            add(bubble);
        }
        if (progress.lastDmResponse() != null) {
            var tokenCount = progress.contextTrace() != null
                    ? progress.contextTrace().totalTokens()
                    : 0;
            var compacted = progress.compactionResult() != null;
            var bubble = createDmBubble(
                    progress.turnNumber(),
                    progress.lastDmResponse(),
                    isAttackTurn(progress),
                    progress.injectionState(),
                    progress.turnType(),
                    progress.attackStrategy(),
                    progress.worstVerdict(),
                    tokenCount,
                    compacted,
                    progress.turnDurationMs());
            add(bubble);
        }
    }

    /**
     * Append only the DM bubble from a progress event.
     * Used when the player bubble was already shown via a pre-turn event.
     */
    public void appendDmBubble(SimulationProgress progress) {
        if (progress.lastDmResponse() == null) {
            return;
        }
        var tokenCount = progress.contextTrace() != null
                ? progress.contextTrace().totalTokens()
                : 0;
        var compacted = progress.compactionResult() != null;
        var bubble = createDmBubble(
                progress.turnNumber(),
                progress.lastDmResponse(),
                isAttackTurn(progress),
                progress.injectionState(),
                progress.turnType(),
                progress.attackStrategy(),
                progress.worstVerdict(),
                tokenCount,
                compacted,
                progress.turnDurationMs());
        add(bubble);
    }

    /**
     * Append a system message (e.g., completion, cancellation).
     */
    public void appendSystemMessage(String message) {
        var div = new Div(new Span(message));
        div.addClassName("ar-system-message");
        add(div);
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        removeAll();
        selectedBubble = null;
        showPlaceholder();
    }

    /**
     * Set the callback invoked when a turn bubble is clicked.
     */
    public void setTurnSelectionCallback(Consumer<Integer> callback) {
        this.turnSelectionCallback = callback;
    }

    // -------------------------------------------------------------------------
    // Bubble creation
    // -------------------------------------------------------------------------

    private Div createPlayerBubble(int turnNumber, String text, boolean isAttack,
                                   boolean injectionEnabled, TurnType turnType,
                                   @Nullable AttackStrategy attackStrategy) {
        var bubble = new Div();
        bubble.addClassName("ar-bubble");
        bubble.addClassName("ar-bubble--player");
        if (isAttack) {
            bubble.addClassName("ar-bubble--attack");
        }

        var header = turnHeader(turnNumber, "Player", injectionEnabled, turnType, attackStrategy, null, null, false);
        var content = new Span(text);
        content.addClassName("ar-bubble-content");

        bubble.add(header, content);
        wireClickToSelect(bubble, turnNumber);
        return bubble;
    }

    private Div createDmBubble(int turnNumber, String text, boolean isAttack,
                               boolean injectionEnabled, TurnType turnType,
                               @Nullable AttackStrategy attackStrategy,
                               EvalVerdict verdict, int tokenCount,
                               boolean compacted, long turnDurationMs) {
        var bubble = new Div();
        bubble.addClassName("ar-bubble");
        bubble.addClassName("ar-bubble--dm");
        if (isAttack) {
            bubble.addClassName("ar-bubble--attack");
        }
        var verdictName = verdict == null ? "neutral" : verdict.verdict().name().toLowerCase().replace('_', '-');
        bubble.getElement().setAttribute("data-verdict", verdictName);

        var header = turnHeader(turnNumber, "DM", injectionEnabled, turnType, attackStrategy, verdict,
                                tokenCount > 0 ? tokenCount : null, compacted, turnDurationMs);
        var content = new Span(text);
        content.addClassName("ar-bubble-content");

        bubble.add(header, content);
        wireClickToSelect(bubble, turnNumber);
        return bubble;
    }

    // -------------------------------------------------------------------------
    // Turn header with injection state tag and turn type badge
    // -------------------------------------------------------------------------

    private HorizontalLayout turnHeader(int turnNumber, String speaker,
                                        boolean injectionEnabled, TurnType turnType,
                                        @Nullable AttackStrategy attackStrategy,
                                        EvalVerdict verdict,
                                        @Nullable Integer tokenCount,
                                        boolean compacted) {
        return turnHeader(turnNumber, speaker, injectionEnabled, turnType, attackStrategy, verdict,
                          tokenCount, compacted, 0L);
    }

    private HorizontalLayout turnHeader(int turnNumber, String speaker,
                                        boolean injectionEnabled, TurnType turnType,
                                        @Nullable AttackStrategy attackStrategy,
                                        EvalVerdict verdict,
                                        @Nullable Integer tokenCount,
                                        boolean compacted,
                                        long turnDurationMs) {
        var layout = new HorizontalLayout();
        layout.setSpacing(true);
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        layout.addClassName("ar-bubble-header");

        var turnLabel = new Span("[T%d] %s".formatted(turnNumber, speaker));
        turnLabel.addClassName("ar-turn-label");

        var injTag = injectionStateTag(injectionEnabled);
        layout.add(turnLabel, injTag);

        if (turnType != null) {
            var badge = turnTypeBadge(turnType, attackStrategy);
            layout.add(badge);
        }

        if (verdict != null) {
            var verdictBadge = driftVerdictBadge(verdict, turnType);
            layout.add(verdictBadge);
        }

        if (tokenCount != null) {
            layout.add(tokenCountBadge(tokenCount));
        }

        if (compacted) {
            layout.add(compactionBadge());
        }

        if (turnDurationMs > 0) {
            layout.add(timingBadge(turnDurationMs));
        }

        return layout;
    }

    private Span injectionStateTag(boolean enabled) {
        var tag = new Span(enabled ? "INJ ON" : "INJ OFF");
        tag.addClassName("ar-badge");
        tag.getElement().setAttribute("data-injection", enabled ? "on" : "off");
        return tag;
    }

    private Span turnTypeBadge(TurnType turnType, @Nullable AttackStrategy attackStrategy) {
        var strategyLabel = (attackStrategy != null)
                ? STRATEGY_CATALOG.findById(attackStrategy.name())
                        .map(DriftStrategyDefinition::displayName)
                        .orElse(attackStrategy.name().replace('_', ' '))
                : null;
        var text = strategyLabel != null
                ? turnType.name() + " · " + strategyLabel
                : turnType.name();
        var badge = new Span(text);
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-turn-type", turnType.name().toLowerCase().replace('_', '-'));
        return badge;
    }

    private Span driftVerdictBadge(EvalVerdict verdict, TurnType turnType) {
        var text = switch (verdict.verdict()) {
            case CONTRADICTED -> "DRIFT: " + verdict.severity().name();
            case CONFIRMED -> confirmedLabel(turnType);
            case NOT_MENTIONED -> "NOT EVALUATED";
        };
        var badge = new Span(text);
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-verdict", verdict.verdict().name().toLowerCase().replace('_', '-'));
        return badge;
    }

    private Span tokenCountBadge(int tokens) {
        var badge = new Span("~%d tok".formatted(tokens));
        badge.addClassName("ar-badge");
        badge.addClassName("ar-badge--muted");
        return badge;
    }

    private Span compactionBadge() {
        var badge = new Span("COMPACTED");
        badge.addClassName("ar-badge");
        badge.addClassName("ar-badge--compaction");
        return badge;
    }

    private Span timingBadge(long durationMs) {
        var badge = new Span(SimulationView.formatDuration(durationMs));
        badge.addClassName("ar-badge");
        if (durationMs > 30_000L) {
            badge.addClassName("ar-badge--timing-warn");
        } else {
            badge.addClassName("ar-badge--muted");
        }
        return badge;
    }

    /**
     * Legacy placeholder; no longer needed as compactionResult() is now integrated into SimulationProgress
     * and the badge is rendered as part of the turn header in createDmBubble() via the compacted parameter.
     *
     * @deprecated Kept for API compatibility; compaction state is now handled by appendTurn/appendDmBubble.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    @SuppressWarnings("unused")
    public void appendCompactionBadge(int turnNumber) {
        // No-op: compaction badge is now rendered as part of the turn header.
    }

    private String confirmedLabel(TurnType turnType) {
        if (turnType == null) {
            return "CONFIRMED";
        }
        return switch (turnType) {
            case ATTACK, DRIFT, DISPLACEMENT -> "HELD";
            case RECALL_PROBE -> "RECALLED";
            default -> "CONFIRMED";
        };
    }

    // -------------------------------------------------------------------------
    // Click-to-select
    // -------------------------------------------------------------------------

    private void wireClickToSelect(Div bubble, int turnNumber) {
        bubble.addClickListener(e -> {
            if (selectedBubble != null) {
                selectedBubble.removeClassName("ar-bubble--selected");
            }
            bubble.addClassName("ar-bubble--selected");
            selectedBubble = bubble;

            if (turnSelectionCallback != null) {
                turnSelectionCallback.accept(turnNumber);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isAttackTurn(SimulationProgress progress) {
        if (progress.turnType() != null) {
            return switch (progress.turnType()) {
                case ATTACK, DRIFT, DISPLACEMENT -> true;
                default -> false;
            };
        }
        return progress.phase() == SimulationProgress.SimulationPhase.ATTACK;
    }

    private void showPlaceholder() {
        var placeholder = new Paragraph("Select a scenario and click Run to start the simulation.");
        placeholder.addClassName("ar-placeholder");
        add(placeholder);
    }
}
