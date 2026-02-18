package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord.TurnSnapshot;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Vaadin view for inspecting a completed simulation run's turn-by-turn data.
 * <p>
 * Accessible at {@code /run?runId=X} for single-run inspection, or
 * {@code /run?runId=X&compare=Y} for side-by-side cross-run comparison.
 * <p>
 * Layout: sidebar with clickable turn list, tabbed main area with
 * Conversation, Anchors, Drift, and (optionally) Diff tabs.
 */
@Route("run")
@PageTitle("Run Inspector")
public class RunInspectorView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger logger = LoggerFactory.getLogger(RunInspectorView.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final RunHistoryStore runStore;

    // State
    private @Nullable SimulationRunRecord primaryRun;
    private @Nullable SimulationRunRecord compareRun;
    private int selectedTurnIndex = -1;

    // UI components
    private final VerticalLayout mainContent;
    private final VerticalLayout errorContent;

    public RunInspectorView(SimulationService simulationService) {
        this.runStore = simulationService.getRunStore();
        setSizeFull();
        setPadding(true);
        setSpacing(false);

        mainContent = new VerticalLayout();
        mainContent.setSizeFull();
        mainContent.setPadding(false);
        mainContent.setSpacing(false);

        errorContent = new VerticalLayout();
        errorContent.setSizeFull();
        errorContent.setPadding(true);
        errorContent.setVisible(false);

        add(mainContent, errorContent);
        setFlexGrow(1, mainContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        var runIdList = params.get("runId");
        var compareList = params.get("compare");

        if (runIdList == null || runIdList.isEmpty()) {
            showError("No runId provided. Navigate from Run History.");
            return;
        }

        var runId = runIdList.getFirst();
        var run = runStore.load(runId);
        if (run.isEmpty()) {
            showError("Run '%s' not found. It may have been evicted from history.".formatted(runId));
            return;
        }

        primaryRun = run.get();

        if (compareList != null && !compareList.isEmpty()) {
            var compareId = compareList.getFirst();
            var comp = runStore.load(compareId);
            comp.ifPresent(record -> compareRun = record);
        }

        buildInspectorUI();
    }

    private void showError(String message) {
        mainContent.setVisible(false);
        errorContent.setVisible(true);
        errorContent.removeAll();
        var icon = new Span("Error");
        icon.getStyle()
            .set("color", "var(--lumo-error-text-color)")
            .set("font-weight", "bold")
            .set("font-size", "var(--lumo-font-size-l)");
        var msg = new Paragraph(message);
        msg.getStyle().set("color", "var(--lumo-secondary-text-color)");
        errorContent.add(icon, msg);
    }

    private void buildInspectorUI() {
        mainContent.removeAll();
        mainContent.setVisible(true);
        errorContent.setVisible(false);

        if (primaryRun == null) {
            return;
        }

        // Header
        var title = compareRun != null
                ? "Comparing: %s vs %s".formatted(primaryRun.scenarioId(), compareRun.scenarioId())
                : "Run Inspector: %s".formatted(primaryRun.scenarioId());
        var header = new H2(title);
        header.getStyle().set("margin", "0 0 8px 0");

        var subtitle = new Span("Started: %s | Turns: %d | Resilience: %.0f%%".formatted(
                DATE_FORMAT.format(primaryRun.startedAt()),
                primaryRun.turnSnapshots().size(),
                primaryRun.resilienceRate() * 100));
        subtitle.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "12px");

        // Sidebar: turn list with verdict indicator
        var turnListBox = new ListBox<TurnSnapshot>();
        turnListBox.setItems(primaryRun.turnSnapshots());
        turnListBox.setRenderer(new ComponentRenderer<>(snap -> {
            var row = new HorizontalLayout();
            row.setSpacing(true);
            row.setAlignItems(Alignment.CENTER);
            row.getStyle().set("padding", "2px 0");

            // Verdict indicator dot
            var dot = new Span();
            dot.getStyle()
               .set("width", "8px")
               .set("height", "8px")
               .set("border-radius", "50%")
               .set("display", "inline-block")
               .set("flex-shrink", "0");
            var worst = snap.worstVerdict();
            if (worst == null) {
                dot.getStyle().set("background", "var(--lumo-contrast-30pct)");
            } else {
                dot.getStyle().set("background", verdictColor(worst.verdict()));
            }

            var label = new Span("Turn %d — %s".formatted(snap.turnNumber(), snap.turnType().name()));
            label.getStyle().set("font-size", "var(--lumo-font-size-s)");

            row.add(dot, label);
            return row;
        }));
        turnListBox.setWidthFull();
        turnListBox.addValueChangeListener(e -> {
            var selected = e.getValue();
            if (selected != null) {
                selectedTurnIndex = primaryRun.turnSnapshots().indexOf(selected);
                updateTabs();
            }
        });

        var sidebar = new VerticalLayout();
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.setWidth("220px");
        sidebar.add(new H4("Turns"));

        var turnScroller = new Scroller(turnListBox);
        turnScroller.setSizeFull();
        turnScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        sidebar.add(turnScroller);
        sidebar.setFlexGrow(1, turnScroller);

        // Main tabs
        var tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        var conversationTab = buildConversationTab();
        var anchorsTab = buildAnchorsTab();
        var driftTab = buildDriftTab();

        tabSheet.add("Conversation", conversationTab);
        tabSheet.add("Anchors", anchorsTab);
        tabSheet.add("Drift", driftTab);

        if (compareRun != null) {
            tabSheet.add("Comparison", buildComparisonTab());
        } else {
            tabSheet.add("Anchor Diff", buildDiffTab());
        }

        var splitLayout = new SplitLayout(sidebar, tabSheet);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(20);

        mainContent.add(header, subtitle, splitLayout);
        mainContent.setFlexGrow(1, splitLayout);

        // Select first turn
        if (!primaryRun.turnSnapshots().isEmpty()) {
            turnListBox.setValue(primaryRun.turnSnapshots().getFirst());
        }
    }

    // -------------------------------------------------------------------------
    // Tabs
    // -------------------------------------------------------------------------

    private VerticalLayout conversationContent;

    private VerticalLayout buildConversationTab() {
        conversationContent = new VerticalLayout();
        conversationContent.setPadding(true);
        conversationContent.setSpacing(true);
        conversationContent.setSizeFull();
        conversationContent.getStyle().set("overflow-y", "auto");
        return conversationContent;
    }

    private VerticalLayout anchorsTabContent;

    private VerticalLayout buildAnchorsTab() {
        anchorsTabContent = new VerticalLayout();
        anchorsTabContent.setPadding(true);
        anchorsTabContent.setSpacing(true);
        anchorsTabContent.setSizeFull();
        anchorsTabContent.getStyle().set("overflow-y", "auto");
        return anchorsTabContent;
    }

    private VerticalLayout driftTabContent;

    private VerticalLayout buildDriftTab() {
        driftTabContent = new VerticalLayout();
        driftTabContent.setPadding(true);
        driftTabContent.setSpacing(true);
        driftTabContent.setSizeFull();
        driftTabContent.getStyle().set("overflow-y", "auto");
        return driftTabContent;
    }

    private VerticalLayout diffTabContent;

    private VerticalLayout buildDiffTab() {
        diffTabContent = new VerticalLayout();
        diffTabContent.setPadding(true);
        diffTabContent.setSpacing(true);
        diffTabContent.setSizeFull();
        diffTabContent.getStyle().set("overflow-y", "auto");
        showPlaceholder(diffTabContent,
                        "Select a turn after turn 1 to see anchor changes from the previous turn.");
        return diffTabContent;
    }

    private VerticalLayout comparisonTabContent;

    private VerticalLayout buildComparisonTab() {
        comparisonTabContent = new VerticalLayout();
        comparisonTabContent.setPadding(true);
        comparisonTabContent.setSpacing(true);
        comparisonTabContent.setSizeFull();
        comparisonTabContent.getStyle().set("overflow-y", "auto");
        return comparisonTabContent;
    }

    // -------------------------------------------------------------------------
    // Tab updates on turn selection
    // -------------------------------------------------------------------------

    private void updateTabs() {
        if (primaryRun == null || selectedTurnIndex < 0
            || selectedTurnIndex >= primaryRun.turnSnapshots().size()) {
            return;
        }

        var snap = primaryRun.turnSnapshots().get(selectedTurnIndex);
        updateConversationTab(snap);
        updateAnchorsTab(snap);
        updateDriftTab(snap);

        if (compareRun != null) {
            updateComparisonTab(snap);
        } else {
            updateDiffTab(snap);
        }
    }

    private void updateConversationTab(TurnSnapshot snap) {
        conversationContent.removeAll();

        var turnHeader = new Span("Turn %d — %s".formatted(snap.turnNumber(), snap.turnType().name()));
        turnHeader.getStyle()
                  .set("font-weight", "bold")
                  .set("font-size", "var(--lumo-font-size-m)")
                  .set("margin-bottom", "8px")
                  .set("display", "block");
        conversationContent.add(turnHeader);

        // Player message
        if (snap.playerMessage() != null) {
            var playerBubble = messageBubble("Player", snap.playerMessage(),
                                             "var(--anchor-accent-amber)", "var(--lumo-contrast-5pct)");
            conversationContent.add(playerBubble);
        }

        // DM response
        if (snap.dmResponse() != null) {
            var dmBubble = messageBubble("DM", snap.dmResponse(),
                                         "var(--anchor-accent-cyan)", "var(--lumo-contrast-5pct)");
            conversationContent.add(dmBubble);
        }

        // Verdict badges for current turn
        var worst = snap.worstVerdict();
        if (worst != null) {
            var verdictRow = new HorizontalLayout();
            verdictRow.setSpacing(true);
            verdictRow.setAlignItems(Alignment.CENTER);
            var verdictLabel = new Span("Verdict: ");
            verdictLabel.getStyle()
                        .set("font-size", "var(--lumo-font-size-s)")
                        .set("color", "var(--lumo-secondary-text-color)");
            verdictRow.add(verdictLabel);
            for (var v : snap.verdicts()) {
                verdictRow.add(verdictBadge(v.verdict()));
            }
            conversationContent.add(verdictRow);
        }

        // Context up to this turn
        conversationContent.add(new Span(""));
        var historyHeader = new Span("Conversation up to turn %d:".formatted(snap.turnNumber()));
        historyHeader.getStyle()
                     .set("font-weight", "bold")
                     .set("font-size", "var(--lumo-font-size-s)")
                     .set("color", "var(--lumo-secondary-text-color)")
                     .set("display", "block");
        conversationContent.add(historyHeader);

        for (int i = 0; i < selectedTurnIndex; i++) {
            var prev = primaryRun.turnSnapshots().get(i);
            if (prev.playerMessage() != null) {
                var bubble = messageBubble("P", prev.playerMessage(),
                                           "var(--anchor-accent-amber)", "transparent");
                bubble.getStyle().set("opacity", "0.6");
                conversationContent.add(bubble);
            }
            if (prev.dmResponse() != null) {
                var dmRow = new HorizontalLayout();
                dmRow.setSpacing(true);
                dmRow.setAlignItems(Alignment.CENTER);
                dmRow.setWidthFull();

                var bubble = messageBubble("DM", prev.dmResponse(),
                                           "var(--anchor-accent-cyan)", "transparent");
                bubble.getStyle().set("opacity", "0.6").set("flex-grow", "1");
                dmRow.add(bubble);

                // Worst verdict badge for this historical turn
                var prevWorst = prev.worstVerdict();
                if (prevWorst != null) {
                    var badge = verdictBadge(prevWorst.verdict());
                    badge.getStyle().set("opacity", "0.6");
                    dmRow.add(badge);
                }

                conversationContent.add(dmRow);
            }
        }
    }

    private void updateAnchorsTab(TurnSnapshot snap) {
        anchorsTabContent.removeAll();

        var turnHeader = new Span("Active anchors at turn %d".formatted(snap.turnNumber()));
        turnHeader.getStyle()
                  .set("font-weight", "bold")
                  .set("display", "block")
                  .set("margin-bottom", "8px");
        anchorsTabContent.add(turnHeader);

        if (snap.activeAnchors() == null || snap.activeAnchors().isEmpty()) {
            showPlaceholder(anchorsTabContent, "No active anchors at this turn.");
            return;
        }

        for (var anchor : snap.activeAnchors()) {
            anchorsTabContent.add(anchorCard(anchor));
        }
    }

    private void updateDriftTab(TurnSnapshot snap) {
        driftTabContent.removeAll();

        var turnHeader = new Span("Drift verdicts at turn %d".formatted(snap.turnNumber()));
        turnHeader.getStyle()
                  .set("font-weight", "bold")
                  .set("display", "block")
                  .set("margin-bottom", "8px");
        driftTabContent.add(turnHeader);

        if (snap.verdicts() == null || snap.verdicts().isEmpty()) {
            showPlaceholder(driftTabContent, "No verdicts for this turn.");
            return;
        }

        for (var verdict : snap.verdicts()) {
            driftTabContent.add(verdictCard(verdict));
        }

        // Overall drift summary
        driftTabContent.add(new Span(""));
        var summaryHeader = new Span("Overall Drift Summary:");
        summaryHeader.getStyle()
                     .set("font-weight", "bold")
                     .set("display", "block")
                     .set("margin-top", "16px");
        driftTabContent.add(summaryHeader);

        long contradictions = primaryRun.turnSnapshots().stream()
                                        .flatMap(t -> t.verdicts() != null ? t.verdicts().stream() : java.util.stream.Stream.empty())
                                        .filter(v -> v.verdict() == EvalVerdict.Verdict.CONTRADICTED)
                                        .count();
        long confirmed = primaryRun.turnSnapshots().stream()
                                   .flatMap(t -> t.verdicts() != null ? t.verdicts().stream() : java.util.stream.Stream.empty())
                                   .filter(v -> v.verdict() == EvalVerdict.Verdict.CONFIRMED)
                                   .count();
        long notMentioned = primaryRun.turnSnapshots().stream()
                                      .flatMap(t -> t.verdicts() != null ? t.verdicts().stream() : java.util.stream.Stream.empty())
                                      .filter(v -> v.verdict() == EvalVerdict.Verdict.NOT_MENTIONED)
                                      .count();

        driftTabContent.add(metricRow("Contradictions", String.valueOf(contradictions), "var(--lumo-error-color)"));
        driftTabContent.add(metricRow("Confirmed", String.valueOf(confirmed), "var(--lumo-success-color)"));
        driftTabContent.add(metricRow("Not Mentioned", String.valueOf(notMentioned), "var(--lumo-secondary-text-color)"));
        driftTabContent.add(metricRow("Resilience Rate", "%.0f%%".formatted(primaryRun.resilienceRate() * 100),
                                      primaryRun.resilienceRate() >= 0.8 ? "var(--lumo-success-color)" : "var(--lumo-error-color)"));
    }

    private void updateDiffTab(TurnSnapshot currentSnap) {
        if (diffTabContent == null) {
            return;
        }
        diffTabContent.removeAll();

        if (selectedTurnIndex <= 0) {
            showPlaceholder(diffTabContent, "Select a turn after turn 1 to see anchor changes from the previous turn.");
            return;
        }

        var prevSnap = primaryRun.turnSnapshots().get(selectedTurnIndex - 1);
        renderAnchorDiff(diffTabContent,
                         prevSnap.activeAnchors(), currentSnap.activeAnchors(),
                         "Turn %d".formatted(prevSnap.turnNumber()),
                         "Turn %d".formatted(currentSnap.turnNumber()));
    }

    private void updateComparisonTab(TurnSnapshot primarySnap) {
        if (comparisonTabContent == null || compareRun == null) {
            return;
        }
        comparisonTabContent.removeAll();

        var compHeader = new H4("Cross-Run Comparison at Turn %d".formatted(primarySnap.turnNumber()));
        comparisonTabContent.add(compHeader);

        // Find matching turn in compare run
        var compareTurnOpt = compareRun.turnSnapshots().stream()
                                       .filter(t -> t.turnNumber() == primarySnap.turnNumber())
                                       .findFirst();

        if (compareTurnOpt.isEmpty()) {
            showPlaceholder(comparisonTabContent,
                            "Turn %d does not exist in the comparison run.".formatted(primarySnap.turnNumber()));
            return;
        }

        var compareSnap = compareTurnOpt.get();

        // Side-by-side layout
        var leftPanel = new VerticalLayout();
        leftPanel.setPadding(true);
        leftPanel.setSpacing(true);
        leftPanel.add(new Span("Run: %s".formatted(primaryRun.scenarioId())));
        leftPanel.getStyle().set("border-right", "1px solid var(--lumo-contrast-20pct)");

        var rightPanel = new VerticalLayout();
        rightPanel.setPadding(true);
        rightPanel.setSpacing(true);
        rightPanel.add(new Span("Run: %s".formatted(compareRun.scenarioId())));

        // Player messages
        if (primarySnap.playerMessage() != null) {
            leftPanel.add(messageBubble("Player", primarySnap.playerMessage(),
                                        "var(--anchor-accent-amber)", "var(--lumo-contrast-5pct)"));
        }
        if (compareSnap.playerMessage() != null) {
            rightPanel.add(messageBubble("Player", compareSnap.playerMessage(),
                                         "var(--anchor-accent-amber)", "var(--lumo-contrast-5pct)"));
        }

        // DM responses
        if (primarySnap.dmResponse() != null) {
            leftPanel.add(messageBubble("DM", primarySnap.dmResponse(),
                                        "var(--anchor-accent-cyan)", "var(--lumo-contrast-5pct)"));
        }
        if (compareSnap.dmResponse() != null) {
            rightPanel.add(messageBubble("DM", compareSnap.dmResponse(),
                                         "var(--anchor-accent-cyan)", "var(--lumo-contrast-5pct)"));
        }

        // Verdicts for primary
        if (primarySnap.verdicts() != null && !primarySnap.verdicts().isEmpty()) {
            var primaryVerdictHeader = styledLabel("Verdicts (worst: %s)".formatted(
                                                           primarySnap.worstVerdict() != null ? primarySnap.worstVerdict().verdict().name() : "none"),
                                                   "var(--lumo-secondary-text-color)");
            leftPanel.add(primaryVerdictHeader);
            for (var v : primarySnap.verdicts()) {
                leftPanel.add(verdictCard(v));
            }
        }

        // Verdicts for compare
        if (compareSnap.verdicts() != null && !compareSnap.verdicts().isEmpty()) {
            var compareVerdictHeader = styledLabel("Verdicts (worst: %s)".formatted(
                                                           compareSnap.worstVerdict() != null ? compareSnap.worstVerdict().verdict().name() : "none"),
                                                   "var(--lumo-secondary-text-color)");
            rightPanel.add(compareVerdictHeader);
            for (var v : compareSnap.verdicts()) {
                rightPanel.add(verdictCard(v));
            }
        }

        var sideBySide = new HorizontalLayout(leftPanel, rightPanel);
        sideBySide.setSizeFull();
        sideBySide.setFlexGrow(1, leftPanel);
        sideBySide.setFlexGrow(1, rightPanel);
        comparisonTabContent.add(sideBySide);

        // Anchor diff between runs at this turn
        comparisonTabContent.add(new H4("Anchor Diff at Turn %d".formatted(primarySnap.turnNumber())));
        renderAnchorDiff(comparisonTabContent,
                         primarySnap.activeAnchors(), compareSnap.activeAnchors(),
                         primaryRun.scenarioId(), compareRun.scenarioId());
    }

    // -------------------------------------------------------------------------
    // Anchor diff rendering
    // -------------------------------------------------------------------------

    /**
     * Render a diff between two anchor lists, showing added, removed, and changed anchors.
     * Uses anchor text (lowercased) as the diff key, and shows rank/authority changes.
     */
    private void renderAnchorDiff(VerticalLayout container, List<Anchor> fromAnchors,
                                  List<Anchor> toAnchors, String fromLabel, String toLabel) {
        var from = fromAnchors != null ? fromAnchors : List.<Anchor> of();
        var to = toAnchors != null ? toAnchors : List.<Anchor> of();

        var fromMap = anchorsByText(from);
        var toMap = anchorsByText(to);

        var fromKeys = new HashSet<>(fromMap.keySet());
        var toKeys = new HashSet<>(toMap.keySet());

        var added = new HashSet<>(toKeys);
        added.removeAll(fromKeys);

        var removed = new HashSet<>(fromKeys);
        removed.removeAll(toKeys);

        var common = new HashSet<>(fromKeys);
        common.retainAll(toKeys);

        // Added
        if (!added.isEmpty()) {
            var addedHeader = styledLabel("+ Added (%d)".formatted(added.size()), "var(--lumo-success-color)");
            container.add(addedHeader);
            for (var key : added) {
                var a = toMap.get(key);
                container.add(diffRow("[%s] %s (rank: %d)".formatted(a.authority(), a.text(), a.rank()),
                                      "var(--lumo-success-color-10pct)", "var(--lumo-success-color)"));
            }
        }

        // Removed
        if (!removed.isEmpty()) {
            var removedHeader = styledLabel("- Removed (%d)".formatted(removed.size()), "var(--lumo-error-color)");
            container.add(removedHeader);
            for (var key : removed) {
                var a = fromMap.get(key);
                container.add(diffRow("[%s] %s (rank: %d)".formatted(a.authority(), a.text(), a.rank()),
                                      "var(--lumo-error-color-10pct)", "var(--lumo-error-color)"));
            }
        }

        // Changed (same text, different rank or authority)
        var changed = new ArrayList<String>();
        for (var key : common) {
            var fa = fromMap.get(key);
            var ta = toMap.get(key);
            if (fa.rank() != ta.rank() || fa.authority() != ta.authority()) {
                changed.add(key);
            }
        }
        if (!changed.isEmpty()) {
            var changedHeader = styledLabel("~ Changed (%d)".formatted(changed.size()), "var(--anchor-accent-amber)");
            container.add(changedHeader);
            for (var key : changed) {
                var fa = fromMap.get(key);
                var ta = toMap.get(key);
                var row = new VerticalLayout();
                row.setPadding(false);
                row.setSpacing(false);
                row.add(diffRow("%s: [%s] rank %d".formatted(fromLabel, fa.authority(), fa.rank()),
                                "var(--lumo-error-color-10pct)", "var(--lumo-secondary-text-color)"));
                row.add(diffRow("%s: [%s] rank %d".formatted(toLabel, ta.authority(), ta.rank()),
                                "var(--lumo-success-color-10pct)", "var(--lumo-secondary-text-color)"));
                var textLabel = new Span(fa.text());
                textLabel.getStyle()
                         .set("font-size", "var(--lumo-font-size-xs)")
                         .set("color", "var(--lumo-secondary-text-color)")
                         .set("padding-left", "12px");
                row.add(textLabel);
                container.add(row);
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            showPlaceholder(container, "No anchor changes between %s and %s.".formatted(fromLabel, toLabel));
        }
    }

    /**
     * Index anchors by their lowercased text for diff comparison.
     */
    private Map<String, Anchor> anchorsByText(List<Anchor> anchors) {
        var map = new HashMap<String, Anchor>();
        for (var anchor : anchors) {
            map.put(anchor.text().toLowerCase(), anchor);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // UI helper methods
    // -------------------------------------------------------------------------

    private Div messageBubble(String role, String text, String borderColor, String bgColor) {
        var bubble = new Div();
        bubble.getStyle()
              .set("border-left", "3px solid " + borderColor)
              .set("background", bgColor)
              .set("border-radius", "var(--lumo-border-radius-m)")
              .set("padding", "6px 10px")
              .set("margin-bottom", "4px")
              .set("font-size", "var(--lumo-font-size-s)");

        var roleSpan = new Span(role + ": ");
        roleSpan.getStyle()
                .set("font-weight", "bold")
                .set("color", borderColor);
        bubble.add(roleSpan);
        bubble.add(new Span(text));
        return bubble;
    }

    /**
     * Rich anchor card showing authority badge, text, rank bar, pinned status,
     * reinforcement count, and trust score.
     */
    private Div anchorCard(Anchor anchor) {
        var card = new Div();
        card.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "8px 12px")
            .set("margin-bottom", "6px");

        // Top row: authority badge + text + pinned indicator
        var topRow = new HorizontalLayout();
        topRow.setSpacing(true);
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setWidthFull();

        var authorityBadge = new Span(anchor.authority().name());
        authorityBadge.getStyle()
                      .set("font-size", "var(--lumo-font-size-xs)")
                      .set("font-weight", "bold")
                      .set("padding", "1px 6px")
                      .set("border-radius", "var(--lumo-border-radius-s)")
                      .set("color", "white")
                      .set("background", authorityColor(anchor.authority()));

        var anchorText = new Span(anchor.text());
        anchorText.getStyle()
                  .set("font-size", "var(--lumo-font-size-s)")
                  .set("flex-grow", "1");

        topRow.add(authorityBadge, anchorText);

        if (anchor.pinned()) {
            var pinned = new Span("PINNED");
            pinned.getStyle()
                  .set("font-size", "var(--lumo-font-size-xs)")
                  .set("font-weight", "bold")
                  .set("color", "var(--lumo-primary-color)")
                  .set("border", "1px solid var(--lumo-primary-color)")
                  .set("border-radius", "var(--lumo-border-radius-s)")
                  .set("padding", "0 4px");
            topRow.add(pinned);
        }

        card.add(topRow);

        // Rank progress bar
        var rankRow = new HorizontalLayout();
        rankRow.setSpacing(true);
        rankRow.setAlignItems(Alignment.CENTER);
        rankRow.setWidthFull();
        rankRow.getStyle().set("margin-top", "4px");

        var rankLabel = new Span("Rank: %d".formatted(anchor.rank()));
        rankLabel.getStyle()
                 .set("font-size", "var(--lumo-font-size-xs)")
                 .set("min-width", "60px");

        var barOuter = new Div();
        barOuter.getStyle()
                .set("flex-grow", "1")
                .set("height", "6px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("border-radius", "3px")
                .set("overflow", "hidden");

        var barInner = new Div();
        var pct = Math.min(100, (int) (anchor.rank() / 9.0));
        String barColor;
        if (anchor.rank() >= 700) {
            barColor = "var(--lumo-error-color)";
        } else if (anchor.rank() >= 400) {
            barColor = "#e67e22";
        } else {
            barColor = "var(--lumo-primary-color)";
        }
        barInner.getStyle()
                .set("width", pct + "%")
                .set("height", "100%")
                .set("background", barColor)
                .set("border-radius", "3px");
        barOuter.add(barInner);

        rankRow.add(rankLabel, barOuter);
        card.add(rankRow);

        // Bottom row: reinforcement count + trust score
        var bottomRow = new HorizontalLayout();
        bottomRow.setSpacing(true);
        bottomRow.getStyle().set("margin-top", "2px");

        var reinforcement = new Span("Reinforced: %dx".formatted(anchor.reinforcementCount()));
        reinforcement.getStyle()
                     .set("font-size", "var(--lumo-font-size-xs)")
                     .set("color", "var(--lumo-secondary-text-color)");
        bottomRow.add(reinforcement);

        if (anchor.trustScore() != null) {
            var trust = new Span("Trust: %.2f".formatted(anchor.trustScore().score()));
            trust.getStyle()
                 .set("font-size", "var(--lumo-font-size-xs)")
                 .set("color", "var(--lumo-secondary-text-color)");
            bottomRow.add(trust);
        }

        card.add(bottomRow);
        return card;
    }

    private Div verdictCard(EvalVerdict verdict) {
        var card = new Div();
        var borderColor = verdictColor(verdict.verdict());
        card.getStyle()
            .set("border-left", "3px solid " + borderColor)
            .set("padding", "6px 10px")
            .set("margin-bottom", "4px")
            .set("font-size", "var(--lumo-font-size-s)");

        var badge = new Span(verdict.verdict().name());
        badge.getStyle()
             .set("font-weight", "bold")
             .set("color", borderColor);
        card.add(badge);

        if (verdict.factId() != null) {
            var factSpan = new Span(" [%s]".formatted(verdict.factId()));
            factSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
            card.add(factSpan);
        }

        if (verdict.explanation() != null && !verdict.explanation().isBlank()) {
            var explanation = new Paragraph(verdict.explanation());
            explanation.getStyle()
                       .set("font-size", "var(--lumo-font-size-xs)")
                       .set("color", "var(--lumo-secondary-text-color)")
                       .set("margin", "4px 0 0 0");
            card.add(explanation);
        }
        return card;
    }

    /**
     * Small colored badge for a verdict value.
     */
    private Span verdictBadge(EvalVerdict.Verdict verdict) {
        var badge = new Span(verdict.name());
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xs)")
             .set("font-weight", "bold")
             .set("padding", "1px 6px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("color", "white")
             .set("background", verdictColor(verdict));
        return badge;
    }

    private String verdictColor(EvalVerdict.Verdict verdict) {
        return switch (verdict) {
            case CONTRADICTED -> "var(--lumo-error-color)";
            case CONFIRMED -> "var(--lumo-success-color)";
            case NOT_MENTIONED -> "var(--lumo-contrast-30pct)";
        };
    }

    private String authorityColor(Authority authority) {
        return switch (authority) {
            case CANON -> "var(--lumo-error-color)";
            case RELIABLE -> "var(--lumo-success-color)";
            case UNRELIABLE -> "#e67e22";
            case PROVISIONAL -> "var(--lumo-contrast-50pct)";
        };
    }

    private HorizontalLayout metricRow(String label, String value, String color) {
        var row = new HorizontalLayout();
        row.setSpacing(true);
        var labelSpan = new Span(label + ":");
        labelSpan.getStyle()
                 .set("font-size", "var(--lumo-font-size-s)")
                 .set("min-width", "140px");
        var valueSpan = new Span(value);
        valueSpan.getStyle()
                 .set("font-size", "var(--lumo-font-size-s)")
                 .set("font-weight", "bold")
                 .set("color", color);
        row.add(labelSpan, valueSpan);
        return row;
    }

    private Div diffRow(String text, String bgColor, String borderColor) {
        var row = new Div();
        row.getStyle()
           .set("background", bgColor)
           .set("border-left", "3px solid " + borderColor)
           .set("padding", "4px 8px")
           .set("margin-bottom", "2px")
           .set("font-size", "var(--lumo-font-size-xs)")
           .set("border-radius", "var(--lumo-border-radius-s)");
        row.setText(text);
        return row;
    }

    private Span styledLabel(String text, String color) {
        var label = new Span(text);
        label.getStyle()
             .set("font-weight", "bold")
             .set("font-size", "var(--lumo-font-size-s)")
             .set("color", color)
             .set("display", "block")
             .set("margin-top", "12px")
             .set("margin-bottom", "4px");
        return label;
    }

    private void showPlaceholder(VerticalLayout container, String message) {
        var placeholder = new Paragraph(message);
        placeholder.getStyle()
                   .set("color", "var(--lumo-secondary-text-color)")
                   .set("font-style", "italic")
                   .set("text-align", "center");
        container.add(placeholder);
    }
}
