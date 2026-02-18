package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationTurn;
import dev.dunnam.diceanchors.sim.engine.TurnType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Timeline panel showing injection state bands and per-anchor event rows.
 * <p>
 * The top row displays a horizontal band of turn cells colored cyan (injection ON)
 * or amber (injection OFF). Below that, each tracked anchor gets its own event row
 * showing lifecycle events (creation, reinforcement, decay, archive) at turn positions.
 * <p>
 * Supports progressive rendering during a running simulation and cross-panel
 * turn selection via a click callback.
 */
public class AnchorTimelinePanel extends VerticalLayout {

    private static final String COLOR_INJECTION_ON = "#00bcd4";   // cyan
    private static final String COLOR_INJECTION_OFF = "#ffb300";   // amber
    private static final String COLOR_CONFIRMED = "#4caf50";       // green
    private static final String COLOR_CONTRADICTED = "#e91e63";    // magenta
    private static final String COLOR_NOT_MENTIONED = "#9e9e9e";   // gray
    private static final int CELL_WIDTH = 28;
    private static final int CELL_HEIGHT = 20;

    private final HorizontalLayout injectionBand;
    private final VerticalLayout anchorRowsContainer;
    private final Div scrollContainer;

    /**
     * Tracks per-anchor events by anchor ID -> list of turn events.
     */
    private final Map<String, List<AnchorEvent>> anchorEventMap = new LinkedHashMap<>();

    /**
     * Turn data for the injection state band.
     */
    private final List<TurnData> turnDataList = new ArrayList<>();

    private @Nullable Consumer<Integer> turnSelectionListener;
    private int selectedTurn = -1;

    /**
     * Represents a single turn's metadata for the timeline.
     */
    private record TurnData(
            int turnNumber,
            boolean injectionEnabled,
            TurnType turnType,
            EvalVerdict.@Nullable Verdict worstVerdict
    ) {}

    /**
     * Represents a lifecycle event for an anchor at a specific turn.
     */
    public enum AnchorEventType {
        CREATED,
        CREATED_EXTRACTED,
        REINFORCED,
        DECAYED,
        ARCHIVED,
        RANK_CHANGED,
        EVICTED,
        AUTHORITY_CHANGED
    }

    private record AnchorEvent(int turnNumber, AnchorEventType eventType) {}

    public AnchorTimelinePanel() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Injection state band
        injectionBand = new HorizontalLayout();
        injectionBand.setSpacing(false);
        injectionBand.setPadding(false);
        injectionBand.getStyle().set("min-height", CELL_HEIGHT + "px");

        // Anchor event rows
        anchorRowsContainer = new VerticalLayout();
        anchorRowsContainer.setPadding(false);
        anchorRowsContainer.setSpacing(false);

        // Scroll container
        scrollContainer = new Div();
        scrollContainer.getStyle()
                       .set("overflow-x", "auto")
                       .set("overflow-y", "auto");
        scrollContainer.add(injectionBand, anchorRowsContainer);

        var bandLabel = new Span("Injection State");
        bandLabel.getStyle()
                 .set("font-size", "var(--lumo-font-size-xs)")
                 .set("font-weight", "bold")
                 .set("color", "var(--lumo-secondary-text-color)");

        add(bandLabel, scrollContainer);
        setFlexGrow(1, scrollContainer);

        showPlaceholder("Run a simulation to see the anchor timeline.");
    }

    /**
     * Register a listener for turn selection events from the timeline.
     */
    public void setTurnSelectionListener(Consumer<Integer> listener) {
        this.turnSelectionListener = listener;
    }

    /**
     * Append a turn to the timeline. Called progressively during simulation.
     */
    public void appendTurn(SimulationProgress progress) {
        var worst = progress.worstVerdict();
        var worstVerdict = worst != null ? worst.verdict() : null;

        var parsedType = progress.turnType() != null
                ? progress.turnType()
                : fallbackTurnType(progress);

        var turnData = new TurnData(
                progress.turnNumber(),
                progress.injectionState(),
                parsedType,
                worstVerdict);
        turnDataList.add(turnData);

        // Add cell to injection band
        injectionBand.add(buildInjectionCell(turnData));

        // Update anchor event rows from context trace
        if (progress.contextTrace() != null) {
            updateAnchorEvents(progress);
        }

        rebuildAnchorRows();

        // Auto-scroll to latest turn
        scrollContainer.getElement().executeJs(
                "this.scrollLeft = this.scrollWidth");
    }

    /**
     * Append real anchor events from the simulation engine for a specific turn.
     * Called externally from SimulationView when {@link SimulationTurn#anchorEvents()} are available.
     *
     * @param turnNumber 1-based turn number
     * @param events     anchor events from {@link SimulationTurn.AnchorEvent AnchorEvent}
     */
    public void appendAnchorEvents(int turnNumber, List<SimulationTurn.AnchorEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        for (var event : events) {
            var type = mapEventType(event.eventType());
            // Differentiate SEEDED vs EXTRACTED for CREATED events based on reason
            if (type == AnchorEventType.CREATED && "sim_extraction".equals(event.reason())) {
                type = AnchorEventType.CREATED_EXTRACTED;
            }
            var anchorEvents = anchorEventMap.computeIfAbsent(
                    event.anchorId(), k -> new ArrayList<>());
            anchorEvents.add(new AnchorEvent(turnNumber, type));
        }

        rebuildAnchorRows();
    }

    private AnchorEventType mapEventType(String engineEventType) {
        return switch (engineEventType) {
            case "CREATED" -> AnchorEventType.CREATED;
            case "CREATED_EXTRACTED" -> AnchorEventType.CREATED_EXTRACTED;
            case "REINFORCED" -> AnchorEventType.REINFORCED;
            case "DECAYED" -> AnchorEventType.DECAYED;
            case "ARCHIVED" -> AnchorEventType.ARCHIVED;
            case "RANK_CHANGED" -> AnchorEventType.RANK_CHANGED;
            case "EVICTED" -> AnchorEventType.EVICTED;
            case "AUTHORITY_CHANGED" -> AnchorEventType.AUTHORITY_CHANGED;
            default -> AnchorEventType.CREATED;
        };
    }

    /**
     * Highlight a specific turn in the timeline.
     */
    public void selectTurn(int turnNumber) {
        this.selectedTurn = turnNumber;
        rebuildInjectionBand();
        rebuildAnchorRows();
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        turnDataList.clear();
        anchorEventMap.clear();
        selectedTurn = -1;
        injectionBand.removeAll();
        anchorRowsContainer.removeAll();
        showPlaceholder("Run a simulation to see the anchor timeline.");
    }

    // -------------------------------------------------------------------------
    // Injection band rendering (10.5)
    // -------------------------------------------------------------------------

    private Div buildInjectionCell(TurnData data) {
        var cell = new Div();
        cell.getStyle()
            .set("width", CELL_WIDTH + "px")
            .set("height", CELL_HEIGHT + "px")
            .set("display", "inline-flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("cursor", "pointer")
            .set("border-right", "1px solid var(--lumo-contrast-10pct)")
            .set("background", data.injectionEnabled() ? COLOR_INJECTION_ON : COLOR_INJECTION_OFF);

        // Drift marker shape based on turn type
        var marker = buildDriftMarker(data);
        if (marker != null) {
            cell.add(marker);
        } else {
            var turnLabel = new Span(String.valueOf(data.turnNumber()));
            turnLabel.getStyle()
                     .set("font-size", "8px")
                     .set("color", "white")
                     .set("user-select", "none");
            cell.add(turnLabel);
        }

        if (data.turnNumber() == selectedTurn) {
            cell.getStyle().set("outline", "2px solid white");
        }

        cell.addClickListener(e -> {
            selectedTurn = data.turnNumber();
            rebuildInjectionBand();
            if (turnSelectionListener != null) {
                turnSelectionListener.accept(data.turnNumber());
            }
        });

        cell.getElement().setAttribute("title",
                                       "T%d | %s | %s | INJ %s".formatted(
                                               data.turnNumber(),
                                               data.turnType().name(),
                                               data.worstVerdict() != null ? data.worstVerdict().name() : "NO_VERDICT",
                                               data.injectionEnabled() ? "ON" : "OFF"));

        return cell;
    }

    private @Nullable Span buildDriftMarker(TurnData data) {
        if (data.worstVerdict() == null) {
            return null;
        }

        var marker = new Span();
        String color = switch (data.worstVerdict()) {
            case CONTRADICTED -> COLOR_CONTRADICTED;
            case CONFIRMED -> COLOR_CONFIRMED;
            case NOT_MENTIONED -> COLOR_NOT_MENTIONED;
        };

        String shape = switch (data.turnType()) {
            case ATTACK, DRIFT, DISPLACEMENT -> "\u25C6"; // diamond
            case ESTABLISH -> "\u25A0";                     // square
            default -> "\u25CF";                            // dot (warm-up, recall_probe)
        };

        marker.setText(shape);
        marker.getStyle()
              .set("color", color)
              .set("font-size", "10px")
              .set("user-select", "none");
        return marker;
    }

    private void rebuildInjectionBand() {
        injectionBand.removeAll();
        for (var data : turnDataList) {
            injectionBand.add(buildInjectionCell(data));
        }
    }

    // -------------------------------------------------------------------------
    // Per-anchor event rows (10.6)
    // -------------------------------------------------------------------------

    private void updateAnchorEvents(SimulationProgress progress) {
        if (progress.contextTrace() == null) {
            return;
        }
        var anchors = progress.contextTrace().injectedAnchors();
        if (anchors == null) {
            return;
        }

        int turn = progress.turnNumber();
        for (var anchor : anchors) {
            var events = anchorEventMap.computeIfAbsent(
                    anchor.id(), k -> new ArrayList<>());

            // If this is the first time we see this anchor, record creation
            if (events.isEmpty() || events.stream().noneMatch(
                    e -> e.eventType() == AnchorEventType.CREATED)) {
                events.add(new AnchorEvent(turn, AnchorEventType.CREATED));
            }

            // Track reinforcements (if count increased since last seen)
            if (anchor.reinforcementCount() > 0) {
                long existingReinforcements = events.stream()
                                                    .filter(e -> e.eventType() == AnchorEventType.REINFORCED)
                                                    .count();
                if (anchor.reinforcementCount() > existingReinforcements) {
                    events.add(new AnchorEvent(turn, AnchorEventType.REINFORCED));
                }
            }
        }
    }

    private void rebuildAnchorRows() {
        anchorRowsContainer.removeAll();

        if (anchorEventMap.isEmpty()) {
            return;
        }

        int totalTurns = turnDataList.size();

        for (var entry : anchorEventMap.entrySet()) {
            var anchorId = entry.getKey();
            var events = entry.getValue();

            var row = new HorizontalLayout();
            row.setSpacing(false);
            row.setPadding(false);
            row.getStyle().set("min-height", (CELL_HEIGHT - 4) + "px");

            // Anchor label
            var label = new Span(truncateId(anchorId));
            label.getStyle()
                 .set("font-size", "8px")
                 .set("font-family", "monospace")
                 .set("color", "var(--lumo-secondary-text-color)")
                 .set("min-width", "60px")
                 .set("display", "inline-flex")
                 .set("align-items", "center");
            row.add(label);

            // Event cells per turn
            for (int t = 0; t < totalTurns; t++) {
                int turnNumber = turnDataList.get(t).turnNumber();
                var cell = new Div();
                cell.getStyle()
                    .set("width", CELL_WIDTH + "px")
                    .set("height", (CELL_HEIGHT - 4) + "px")
                    .set("display", "inline-flex")
                    .set("align-items", "center")
                    .set("justify-content", "center")
                    .set("border-right", "1px solid var(--lumo-contrast-5pct)")
                    .set("background", "var(--lumo-contrast-5pct)");

                var eventAtTurn = events.stream()
                                        .filter(e -> e.turnNumber() == turnNumber)
                                        .findFirst()
                                        .orElse(null);

                if (eventAtTurn != null) {
                    var eventMarker = new Span(eventSymbol(eventAtTurn.eventType()));
                    eventMarker.getStyle()
                               .set("font-size", "8px")
                               .set("color", eventColor(eventAtTurn.eventType()));
                    cell.add(eventMarker);
                }

                if (turnNumber == selectedTurn) {
                    cell.getStyle().set("outline", "1px solid var(--lumo-primary-color)");
                }

                row.add(cell);
            }

            anchorRowsContainer.add(row);
        }
    }

    private String eventSymbol(AnchorEventType type) {
        return switch (type) {
            case CREATED -> "+";
            case CREATED_EXTRACTED -> "\u25C8";   // diamond with dot (extracted)
            case REINFORCED -> "\u2191";          // up arrow
            case DECAYED -> "\u2193";             // down arrow
            case ARCHIVED -> "x";
            case RANK_CHANGED -> "\u2195";        // up-down arrow
            case EVICTED -> "\u2717";             // ballot x
            case AUTHORITY_CHANGED -> "\u2605";   // star
        };
    }

    private String eventColor(AnchorEventType type) {
        return switch (type) {
            case CREATED -> COLOR_CONFIRMED;
            case CREATED_EXTRACTED -> "#009688";  // teal (extracted)
            case REINFORCED -> COLOR_INJECTION_ON;
            case DECAYED -> COLOR_INJECTION_OFF;
            case ARCHIVED -> COLOR_CONTRADICTED;
            case RANK_CHANGED -> COLOR_INJECTION_ON;
            case EVICTED -> COLOR_CONTRADICTED;
            case AUTHORITY_CHANGED -> "#9c27b0"; // purple
        };
    }

    private String truncateId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private void showPlaceholder(String message) {
        var placeholder = new Paragraph(message);
        placeholder.getStyle()
                   .set("color", "var(--lumo-secondary-text-color)")
                   .set("font-style", "italic")
                   .set("text-align", "center");
        anchorRowsContainer.add(placeholder);
    }

    private TurnType fallbackTurnType(SimulationProgress progress) {
        return switch (progress.phase()) {
            case WARM_UP -> TurnType.WARM_UP;
            case ESTABLISH -> TurnType.ESTABLISH;
            case ATTACK -> TurnType.ATTACK;
            case EVALUATE -> TurnType.RECALL_PROBE;
            default -> TurnType.ESTABLISH;
        };
    }
}
