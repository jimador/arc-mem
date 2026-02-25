package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
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
 * Timeline panel showing a two-band header (4 px injection strip + drift-marker track)
 * followed by per-anchor event rows using flex-wrap badge pills.
 * <p>
 * The injection strip colors each turn cyan (ON) or amber (OFF). The drift-marker
 * track sits below with a Unicode symbol per turn colored by verdict severity. Each
 * anchor row shows lifecycle events as compact letter-badges in a flex-wrap layout.
 * <p>
 * Public API is unchanged: {@link #appendTurn}, {@link #appendAnchorEvents},
 * {@link #selectTurn}, {@link #reset}.
 */
public class AnchorTimelinePanel extends VerticalLayout implements SimulationProgressListener {

    /** 4 px injection strip: one Div cell per turn. */
    private final HorizontalLayout injectionStrip;

    /** Drift-marker track: one Span per turn with Unicode symbol + verdict color. */
    private final HorizontalLayout driftTrack;

    /** Per-anchor event rows (FlexLayout per anchor). */
    private final VerticalLayout anchorRowsContainer;

    /** Shared horizontal scroll wrapper for all three bands. */
    private final Div scrollContainer;

    private final List<TurnData> turnDataList = new ArrayList<>();

    /** anchorId → anchor metadata (first-seen text + authority). */
    private final Map<String, AnchorMeta> anchorMetaMap = new LinkedHashMap<>();

    /** anchorId → list of timeline events. */
    private final Map<String, List<AnchorEvent>> anchorEventMap = new LinkedHashMap<>();

    private @Nullable Consumer<Integer> turnSelectionListener;
    private int selectedTurn = -1;

    private record TurnData(
            int turnNumber,
            boolean injectionEnabled,
            TurnType turnType,
            EvalVerdict.@Nullable Verdict worstVerdict,
            long turnDurationMs
    ) {}

    private record AnchorMeta(String text, String authority) {}

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

    private record AnchorEvent(int turnNumber, AnchorEventType eventType, String reason) {}

    public AnchorTimelinePanel() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        injectionStrip = new HorizontalLayout();
        injectionStrip.setSpacing(false);
        injectionStrip.setPadding(false);
        injectionStrip.addClassName("ar-injection-strip");

        driftTrack = new HorizontalLayout();
        driftTrack.setSpacing(false);
        driftTrack.setPadding(false);
        driftTrack.addClassName("ar-drift-track");

        anchorRowsContainer = new VerticalLayout();
        anchorRowsContainer.setPadding(false);
        anchorRowsContainer.setSpacing(false);

        scrollContainer = new Div();
        scrollContainer.addClassName("ar-timeline-scroll");
        scrollContainer.add(injectionStrip, driftTrack, anchorRowsContainer);

        var bandLabel = new Span("Injection + Drift");
        bandLabel.addClassName("ar-band-label");

        add(bandLabel, scrollContainer);
        setFlexGrow(1, scrollContainer);

        showPlaceholder("Run a simulation to see the anchor timeline.");
    }

    @Override
    public void onTurnCompleted(SimulationProgress progress) {
        appendTurn(progress);
        if (progress.anchorEvents() != null && !progress.anchorEvents().isEmpty()) {
            appendAnchorEvents(progress.turnNumber(), progress.anchorEvents());
        }
    }

    /** Register a listener for turn selection events from the timeline. */
    public void setTurnSelectionListener(Consumer<Integer> listener) {
        this.turnSelectionListener = listener;
    }

    /** Append a turn to the timeline. Called progressively during simulation. */
    public void appendTurn(SimulationProgress progress) {
        if (progress.lastDmResponse() == null) {
            // Pre-turn thinking event — nothing to add to timeline yet
            return;
        }
        var worst = progress.worstVerdict();
        var worstVerdict = worst != null ? worst.verdict() : null;

        var parsedType = progress.turnType() != null
                ? progress.turnType()
                : fallbackTurnType(progress);

        var turnData = new TurnData(
                progress.turnNumber(),
                progress.injectionState(),
                parsedType,
                worstVerdict,
                progress.turnDurationMs());
        turnDataList.add(turnData);

        injectionStrip.add(buildInjectionCell(turnData));
        driftTrack.add(buildDriftMarker(turnData));

        if (progress.contextTrace() != null) {
            updateAnchorEventsFromTrace(progress);
        }

        rebuildAnchorRows();

        scrollContainer.getElement().executeJs("this.scrollLeft = this.scrollWidth");
    }

    /**
     * Append real anchor events from the simulation engine for a specific turn.
     */
    public void appendAnchorEvents(int turnNumber, List<SimulationTurn.AnchorEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        for (var event : events) {
            // Store metadata on first encounter
            anchorMetaMap.computeIfAbsent(event.anchorId(),
                    k -> new AnchorMeta(
                            event.text() != null ? event.text() : event.anchorId(),
                            event.authority() != null ? event.authority() : "?"));

            var type = mapEventType(event.eventType());
            if (type == AnchorEventType.CREATED && "sim_extraction".equals(event.reason())) {
                type = AnchorEventType.CREATED_EXTRACTED;
            }

            anchorEventMap.computeIfAbsent(event.anchorId(), k -> new ArrayList<>())
                          .add(new AnchorEvent(turnNumber, type,
                                  event.reason() != null ? event.reason() : ""));
        }

        rebuildAnchorRows();
    }

    /** Highlight a specific turn in the timeline. */
    public void selectTurn(int turnNumber) {
        this.selectedTurn = turnNumber;
        rebuildInjectionStrip();
        rebuildDriftTrack();
        rebuildAnchorRows();
    }

    /** Reset the panel to its initial empty state. */
    public void reset() {
        turnDataList.clear();
        anchorMetaMap.clear();
        anchorEventMap.clear();
        selectedTurn = -1;
        injectionStrip.removeAll();
        driftTrack.removeAll();
        anchorRowsContainer.removeAll();
        showPlaceholder("Run a simulation to see the anchor timeline.");
    }

    private Div buildInjectionCell(TurnData data) {
        var cell = new Div();
        cell.addClassName("injection-strip-cell");
        cell.getElement().setAttribute("data-injection", data.injectionEnabled() ? "on" : "off");

        if (data.turnNumber() == selectedTurn) {
            cell.addClassName("ar-turn-selected");
        }

        cell.getElement().setAttribute("title",
                "T%d | %s | INJ %s".formatted(
                        data.turnNumber(),
                        data.turnType().name(),
                        data.injectionEnabled() ? "ON" : "OFF"));

        cell.addClickListener(e -> {
            selectedTurn = data.turnNumber();
            rebuildInjectionStrip();
            rebuildDriftTrack();
            if (turnSelectionListener != null) {
                turnSelectionListener.accept(data.turnNumber());
            }
        });

        return cell;
    }

    private void rebuildInjectionStrip() {
        injectionStrip.removeAll();
        for (var data : turnDataList) {
            injectionStrip.add(buildInjectionCell(data));
        }
    }

    private Span buildDriftMarker(TurnData data) {
        var marker = new Span(driftSymbol(data));
        marker.addClassName("drift-marker");
        marker.addClassName(driftCssClass(data.worstVerdict()));

        if (data.turnNumber() == selectedTurn) {
            marker.addClassName("ar-turn-selected");
        }

        var durationLabel = data.turnDurationMs() > 0
                ? SimulationView.formatDuration(data.turnDurationMs())
                : "—";
        marker.getElement().setAttribute("title",
                "T%d | %s | %s | %s".formatted(
                        data.turnNumber(),
                        data.turnType().name(),
                        data.worstVerdict() != null ? data.worstVerdict().name() : "NO_VERDICT",
                        durationLabel));

        marker.addClickListener(e -> {
            selectedTurn = data.turnNumber();
            rebuildInjectionStrip();
            rebuildDriftTrack();
            if (turnSelectionListener != null) {
                turnSelectionListener.accept(data.turnNumber());
            }
        });

        return marker;
    }

    private String driftSymbol(TurnData data) {
        return switch (data.turnType()) {
            case ATTACK, DRIFT, DISPLACEMENT -> "\u25C7";  // ◇ open diamond
            case ESTABLISH -> "\u25A0";                     // ■ filled square
            case RECALL_PROBE -> "\u25CE";                  // ◎ bullseye
            default -> "\u00B7";                            // · middle dot (WARM_UP)
        };
    }

    private String driftCssClass(EvalVerdict.@Nullable Verdict verdict) {
        if (verdict == null) {
            return "drift-marker-neutral";
        }
        return switch (verdict) {
            case CONTRADICTED -> "drift-marker-contradicted";
            case CONFIRMED -> "drift-marker-confirmed";
            case NOT_MENTIONED -> "drift-marker-neutral";
        };
    }

    private void rebuildDriftTrack() {
        driftTrack.removeAll();
        for (var data : turnDataList) {
            driftTrack.add(buildDriftMarker(data));
        }
    }

    private void updateAnchorEventsFromTrace(SimulationProgress progress) {
        if (progress.contextTrace() == null) {
            return;
        }
        var anchors = progress.contextTrace().injectedAnchors();
        if (anchors == null) {
            return;
        }

        int turn = progress.turnNumber();
        for (var anchor : anchors) {
            anchorMetaMap.computeIfAbsent(anchor.id(),
                    k -> new AnchorMeta(
                            anchor.text() != null ? truncate(anchor.text(), 28) : anchor.id(),
                            anchor.authority() != null ? anchor.authority().name() : "?"));

            var events = anchorEventMap.computeIfAbsent(anchor.id(), k -> new ArrayList<>());

            if (events.stream().noneMatch(e -> e.eventType() == AnchorEventType.CREATED)) {
                events.add(new AnchorEvent(turn, AnchorEventType.CREATED, "trace"));
            }

            if (anchor.reinforcementCount() > 0) {
                long existing = events.stream()
                                      .filter(e -> e.eventType() == AnchorEventType.REINFORCED)
                                      .count();
                if (anchor.reinforcementCount() > existing) {
                    events.add(new AnchorEvent(turn, AnchorEventType.REINFORCED, "trace"));
                }
            }
        }
    }

    private void rebuildAnchorRows() {
        anchorRowsContainer.removeAll();

        if (anchorEventMap.isEmpty()) {
            return;
        }

        for (var entry : anchorEventMap.entrySet()) {
            var anchorId = entry.getKey();
            var events = entry.getValue();
            var meta = anchorMetaMap.get(anchorId);

            var row = new HorizontalLayout();
            row.setSpacing(false);
            row.setPadding(false);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);
            row.addClassName("ar-anchor-row");

            var header = new Span();
            header.addClassName("anchor-row-header");
            if (meta != null) {
                var authorityBadge = new Span(authorityAbbrev(meta.authority()));
                authorityBadge.addClassName("ar-authority-badge");
                authorityBadge.getElement().setAttribute("data-authority",
                        meta.authority() != null ? meta.authority().toLowerCase() : "provisional");
                header.add(authorityBadge);

                var textSpan = new Span(truncate(meta.text(), 20));
                textSpan.addClassName("ar-anchor-text-truncated");
                header.add(textSpan);
            } else {
                header.add(new Span(truncateId(anchorId)));
            }
            row.add(header);

            var badges = new FlexLayout();
            badges.addClassName("ar-anchor-badges");

            for (var event : events) {
                badges.add(buildEventBadge(event));
            }

            row.add(badges);
            anchorRowsContainer.add(row);
        }
    }

    private Span buildEventBadge(AnchorEvent event) {
        var badge = new Span(eventLetter(event.eventType()) + event.turnNumber());
        badge.addClassName("event-badge");
        badge.getElement().setAttribute("data-event-type", eventDataType(event.eventType()));

        badge.getElement().setAttribute("title",
                "%s T%d%s".formatted(
                        event.eventType().name(),
                        event.turnNumber(),
                        event.reason().isBlank() ? "" : " (" + event.reason() + ")"));

        return badge;
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

    private String eventLetter(AnchorEventType type) {
        return switch (type) {
            case CREATED -> "C";
            case CREATED_EXTRACTED -> "E";
            case REINFORCED -> "R";
            case DECAYED -> "D";
            case ARCHIVED -> "A";
            case RANK_CHANGED -> "K";
            case EVICTED -> "V";
            case AUTHORITY_CHANGED -> "U";
        };
    }

    private String eventDataType(AnchorEventType type) {
        return switch (type) {
            case CREATED -> "created";
            case CREATED_EXTRACTED -> "extracted";
            case REINFORCED -> "reinforced";
            case DECAYED -> "decayed";
            case ARCHIVED -> "archived";
            case RANK_CHANGED -> "rank";
            case EVICTED -> "evicted";
            case AUTHORITY_CHANGED -> "authority";
        };
    }

    private String authorityAbbrev(String authority) {
        if (authority == null) return "?";
        return switch (authority.toUpperCase()) {
            case "PROVISIONAL" -> "P";
            case "UNRELIABLE" -> "U";
            case "RELIABLE" -> "R";
            case "CANON" -> "C";
            default -> authority.substring(0, 1).toUpperCase();
        };
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    private String truncateId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private void showPlaceholder(String message) {
        var placeholder = new Paragraph(message);
        placeholder.addClassName("ar-placeholder");
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
