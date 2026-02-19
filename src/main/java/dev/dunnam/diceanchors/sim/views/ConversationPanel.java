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
public class ConversationPanel extends VerticalLayout {

    private static final StrategyCatalog STRATEGY_CATALOG =
            StrategyCatalog.loadFromClasspath("simulations/strategy-catalog.yml");

    private Div selectedBubble;
    private Consumer<Integer> turnSelectionCallback;

    public ConversationPanel() {
        setPadding(true);
        setSpacing(false);
        setSizeFull();
        getStyle()
                .set("overflow-y", "auto")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        showPlaceholder();
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
        div.getStyle()
           .set("text-align", "center")
           .set("color", "var(--lumo-secondary-text-color)")
           .set("font-style", "italic")
           .set("font-size", "var(--lumo-font-size-s)")
           .set("margin", "12px 0")
           .set("padding", "8px")
           .set("border-top", "1px solid var(--lumo-contrast-20pct)");
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
        bubble.getStyle()
              .set("margin", "4px 0")
              .set("padding", "8px 12px")
              .set("border-radius", "var(--lumo-border-radius-m)")
              .set("max-width", "90%")
              .set("cursor", "pointer")
              .set("transition", "outline 0.15s ease")
              .set("background", isAttack
                      ? "var(--lumo-error-color-10pct)"
                      : "var(--lumo-primary-color-10pct)")
              .set("border-left", isAttack
                      ? "3px solid var(--lumo-error-color)"
                      : "3px solid var(--lumo-primary-color)")
              .set("align-self", "flex-start");

        var header = turnHeader(turnNumber, "Player", injectionEnabled, turnType, attackStrategy, null, null, false);
        var content = new Span(text);
        content.getStyle().set("font-size", "var(--lumo-font-size-s)");

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
        var borderColor = verdictBorderColor(verdict);

        bubble.getStyle()
              .set("margin", "4px 0 12px 16px")
              .set("padding", "8px 12px")
              .set("border-radius", "var(--lumo-border-radius-m)")
              .set("max-width", "90%")
              .set("cursor", "pointer")
              .set("transition", "outline 0.15s ease")
              .set("background", isAttack
                      ? "var(--lumo-contrast-5pct)"
                      : "var(--lumo-success-color-10pct)")
              .set("border-left", "3px solid " + borderColor);

        var header = turnHeader(turnNumber, "DM", injectionEnabled, turnType, attackStrategy, verdict,
                                tokenCount > 0 ? tokenCount : null, compacted, turnDurationMs);
        var content = new Span(text);
        content.getStyle().set("font-size", "var(--lumo-font-size-s)");

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
        layout.getStyle()
              .set("margin-bottom", "2px")
              .set("flex-wrap", "wrap");

        var turnLabel = new Span("[T%d] %s".formatted(turnNumber, speaker));
        turnLabel.getStyle()
                 .set("font-weight", "bold")
                 .set("font-size", "var(--lumo-font-size-xs)")
                 .set("color", "var(--lumo-secondary-text-color)");

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
        tag.getStyle()
           .set("font-size", "var(--lumo-font-size-xxs)")
           .set("font-weight", "bold")
           .set("padding", "1px 5px")
           .set("border-radius", "var(--lumo-border-radius-s)")
           .set("white-space", "nowrap")
           .set("color", "white")
           .set("background", enabled ? "#00bcd4" : "#ff9800");
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
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("white-space", "nowrap")
             .set("color", "white")
             .set("background", turnTypeColor(turnType));
        return badge;
    }

    private Span driftVerdictBadge(EvalVerdict verdict, TurnType turnType) {
        var text = switch (verdict.verdict()) {
            case CONTRADICTED -> "DRIFT: " + verdict.severity().name();
            case CONFIRMED -> confirmedLabel(turnType);
            case NOT_MENTIONED -> "NOT EVALUATED";
        };
        var badge = new Span(text);
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("white-space", "nowrap")
             .set("color", "white")
             .set("background", verdictColor(verdict));
        return badge;
    }

    private Span tokenCountBadge(int tokens) {
        var badge = new Span("~%d tok".formatted(tokens));
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("white-space", "nowrap")
             .set("color", "var(--lumo-secondary-text-color)")
             .set("background", "var(--lumo-contrast-10pct)");
        return badge;
    }

    private Span compactionBadge() {
        var badge = new Span("COMPACTED");
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("white-space", "nowrap")
             .set("color", "white")
             .set("background", "#ffb300");
        return badge;
    }

    private Span timingBadge(long durationMs) {
        var badge = new Span(SimulationView.formatDuration(durationMs));
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("white-space", "nowrap")
             .set("color", durationMs > 30_000L ? "white" : "var(--lumo-secondary-text-color)")
             .set("background", durationMs > 30_000L ? "#ffb020" : "var(--lumo-contrast-10pct)");
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

    private String verdictColor(EvalVerdict verdict) {
        return switch (verdict.verdict()) {
            case CONFIRMED -> "#4caf50";
            case NOT_MENTIONED -> "#ff9800";
            case CONTRADICTED -> "#e91e63";
        };
    }

    private String turnTypeColor(TurnType turnType) {
        return switch (turnType) {
            case WARM_UP -> "#607d8b";
            case ESTABLISH -> "#4caf50";
            case ATTACK -> "#f44336";
            case DISPLACEMENT -> "#e91e63";
            case DRIFT -> "#ff5722";
            case RECALL_PROBE -> "#9c27b0";
        };
    }

    // -------------------------------------------------------------------------
    // Verdict-colored left borders for DM bubbles
    // -------------------------------------------------------------------------

    private String verdictBorderColor(EvalVerdict verdict) {
        if (verdict == null) {
            return "#9e9e9e"; // neutral gray
        }
        return switch (verdict.verdict()) {
            case CONFIRMED -> "#4caf50";      // green
            case NOT_MENTIONED -> "#ff9800";   // amber
            case CONTRADICTED -> "#e91e63";    // magenta
        };
    }

    // -------------------------------------------------------------------------
    // Click-to-select
    // -------------------------------------------------------------------------

    private void wireClickToSelect(Div bubble, int turnNumber) {
        bubble.addClickListener(e -> {
            if (selectedBubble != null) {
                selectedBubble.getStyle().remove("outline");
            }
            bubble.getStyle().set("outline", "2px solid var(--lumo-primary-color)");
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
        placeholder.getStyle()
                   .set("color", "var(--lumo-secondary-text-color)")
                   .set("font-style", "italic")
                   .set("text-align", "center")
                   .set("margin", "32px auto");
        add(placeholder);
    }
}
