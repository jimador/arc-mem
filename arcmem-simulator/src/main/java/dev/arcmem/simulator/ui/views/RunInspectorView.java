package dev.arcmem.simulator.ui.views;
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

import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;
import dev.arcmem.simulator.ui.controllers.*;
import dev.arcmem.simulator.ui.panels.*;

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
import com.vaadin.flow.router.RouterLink;
import dev.arcmem.simulator.engine.EvalVerdict;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.history.SimulationRunRecord;
import dev.arcmem.simulator.history.SimulationRunRecord.TurnSnapshot;
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
 * Conversation, Units, Drift, and (optionally) Diff tabs.
 */
@Route("run")
@PageTitle("Run Inspector")
public class RunInspectorView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger logger = LoggerFactory.getLogger(RunInspectorView.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final RunHistoryStore runStore;

    private @Nullable SimulationRunRecord primaryRun;
    private @Nullable SimulationRunRecord compareRun;
    private int selectedTurnIndex = -1;

    private final VerticalLayout mainContent;
    private final VerticalLayout errorContent;

    public RunInspectorView(RunHistoryStore runStore) {
        this.runStore = runStore;
        setSizeFull();
        setPadding(true);
        setSpacing(false);

        var nav = new HorizontalLayout();
        nav.setWidthFull();
        nav.setSpacing(true);
        nav.setAlignItems(Alignment.CENTER);
        nav.setPadding(false);
        nav.addClassName("ar-nav-bar");

        var backLink = new RouterLink("Simulation", SimulationView.class);
        backLink.addClassName("ar-nav-link");
        var benchmarkLink = new RouterLink("Benchmark", BenchmarkView.class);
        benchmarkLink.addClassName("ar-nav-link");

        nav.add(backLink, benchmarkLink);

        mainContent = new VerticalLayout();
        mainContent.setSizeFull();
        mainContent.setPadding(false);
        mainContent.setSpacing(false);

        errorContent = new VerticalLayout();
        errorContent.setSizeFull();
        errorContent.setPadding(true);
        errorContent.setVisible(false);

        add(nav, mainContent, errorContent);
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
        icon.addClassName("ar-error-icon");
        var msg = new Paragraph(message);
        msg.addClassName("ar-error-message");
        errorContent.add(icon, msg);
    }

    private void buildInspectorUI() {
        mainContent.removeAll();
        mainContent.setVisible(true);
        errorContent.setVisible(false);

        if (primaryRun == null) {
            return;
        }

        var title = compareRun != null
                ? "Comparing: %s vs %s".formatted(primaryRun.scenarioId(), compareRun.scenarioId())
                : "Run Inspector: %s".formatted(primaryRun.scenarioId());
        var header = new H2(title);
        header.addClassName("ar-run-header");

        var subtitle = new Span("Started: %s | Turns: %d | Resilience: %.0f%%".formatted(
                DATE_FORMAT.format(primaryRun.startedAt()),
                primaryRun.turnSnapshots().size(),
                primaryRun.resilienceRate() * 100));
        subtitle.addClassName("ar-run-subtitle");

        var turnListBox = new ListBox<TurnSnapshot>();
        turnListBox.setItems(primaryRun.turnSnapshots());
        turnListBox.setRenderer(new ComponentRenderer<>(snap -> {
            var row = new HorizontalLayout();
            row.setSpacing(true);
            row.setAlignItems(Alignment.CENTER);
            row.addClassName("ar-run-turn-row");

            var dot = new Span();
            dot.addClassName("ar-turn-dot");
            var worst = snap.worstVerdict();
            if (worst == null) {
                dot.getElement().setAttribute("data-verdict", "neutral");
            } else {
                dot.getElement().setAttribute("data-verdict", verdictDataValue(worst.verdict()));
            }

            var label = new Span("Turn %d — %s".formatted(snap.turnNumber(), snap.turnType().name()));
            label.addClassName("ar-run-turn-label");

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

        var tabSheet = new TabSheet();
        tabSheet.setSizeFull();

        var conversationTab = buildConversationTab();
        var unitsTab = buildUnitsTab();
        var driftTab = buildDriftTab();

        tabSheet.add("Conversation", conversationTab);
        tabSheet.add("Memory Units", unitsTab);
        tabSheet.add("Drift", driftTab);

        if (compareRun != null) {
            tabSheet.add("Comparison", buildComparisonTab());
        } else {
            tabSheet.add("Unit Diff", buildDiffTab());
        }

        var splitLayout = new SplitLayout(sidebar, tabSheet);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(20);

        mainContent.add(header, subtitle, splitLayout);
        mainContent.setFlexGrow(1, splitLayout);

        if (!primaryRun.turnSnapshots().isEmpty()) {
            turnListBox.setValue(primaryRun.turnSnapshots().getFirst());
        }
    }

    private VerticalLayout conversationContent;

    private VerticalLayout buildConversationTab() {
        conversationContent = new VerticalLayout();
        conversationContent.setPadding(true);
        conversationContent.setSpacing(true);
        conversationContent.setSizeFull();
        conversationContent.addClassName("ar-scrollable");
        return conversationContent;
    }

    private VerticalLayout unitsTabContent;

    private VerticalLayout buildUnitsTab() {
        unitsTabContent = new VerticalLayout();
        unitsTabContent.setPadding(true);
        unitsTabContent.setSpacing(true);
        unitsTabContent.setSizeFull();
        unitsTabContent.addClassName("ar-scrollable");
        return unitsTabContent;
    }

    private VerticalLayout driftTabContent;

    private VerticalLayout buildDriftTab() {
        driftTabContent = new VerticalLayout();
        driftTabContent.setPadding(true);
        driftTabContent.setSpacing(true);
        driftTabContent.setSizeFull();
        driftTabContent.addClassName("ar-scrollable");
        return driftTabContent;
    }

    private VerticalLayout diffTabContent;

    private VerticalLayout buildDiffTab() {
        diffTabContent = new VerticalLayout();
        diffTabContent.setPadding(true);
        diffTabContent.setSpacing(true);
        diffTabContent.setSizeFull();
        diffTabContent.addClassName("ar-scrollable");
        showPlaceholder(diffTabContent,
                        "Select a turn after turn 1 to see memory-unit changes from the previous turn.");
        return diffTabContent;
    }

    private VerticalLayout comparisonTabContent;

    private VerticalLayout buildComparisonTab() {
        comparisonTabContent = new VerticalLayout();
        comparisonTabContent.setPadding(true);
        comparisonTabContent.setSpacing(true);
        comparisonTabContent.setSizeFull();
        comparisonTabContent.addClassName("ar-scrollable");
        return comparisonTabContent;
    }

    private void updateTabs() {
        if (primaryRun == null || selectedTurnIndex < 0
            || selectedTurnIndex >= primaryRun.turnSnapshots().size()) {
            return;
        }

        var snap = primaryRun.turnSnapshots().get(selectedTurnIndex);
        updateConversationTab(snap);
        updateUnitsTab(snap);
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
        turnHeader.addClassName("ar-run-turn-header");
        conversationContent.add(turnHeader);

        if (snap.playerMessage() != null) {
            var playerBubble = messageBubble("Player", snap.playerMessage(), "player");
            conversationContent.add(playerBubble);
        }

        if (snap.dmResponse() != null) {
            var dmBubble = messageBubble("DM", snap.dmResponse(), "dm");
            conversationContent.add(dmBubble);
        }

        var worst = snap.worstVerdict();
        if (worst != null) {
            var verdictRow = new HorizontalLayout();
            verdictRow.setSpacing(true);
            verdictRow.setAlignItems(Alignment.CENTER);
            var verdictLabel = new Span("Verdict: ");
            verdictLabel.addClassName("ar-run-verdict-label");
            verdictRow.add(verdictLabel);
            for (var v : snap.verdicts()) {
                verdictRow.add(verdictBadge(v.verdict()));
            }
            conversationContent.add(verdictRow);
        }

        conversationContent.add(new Span(""));
        var historyHeader = new Span("Conversation up to turn %d:".formatted(snap.turnNumber()));
        historyHeader.addClassName("ar-run-history-header");
        conversationContent.add(historyHeader);

        for (int i = 0; i < selectedTurnIndex; i++) {
            var prev = primaryRun.turnSnapshots().get(i);
            if (prev.playerMessage() != null) {
                var bubble = messageBubble("P", prev.playerMessage(), "player-faded");
                bubble.addClassName("ar-run-bubble--faded");
                conversationContent.add(bubble);
            }
            if (prev.dmResponse() != null) {
                var dmRow = new HorizontalLayout();
                dmRow.setSpacing(true);
                dmRow.setAlignItems(Alignment.CENTER);
                dmRow.setWidthFull();

                var bubble = messageBubble("DM", prev.dmResponse(), "dm-faded");
                bubble.addClassName("ar-run-bubble--faded");
                bubble.addClassName("ar-run-bubble--flex-grow");
                dmRow.add(bubble);

                var prevWorst = prev.worstVerdict();
                if (prevWorst != null) {
                    var badge = verdictBadge(prevWorst.verdict());
                    badge.addClassName("ar-run-bubble--faded");
                    dmRow.add(badge);
                }

                conversationContent.add(dmRow);
            }
        }
    }

    private void updateUnitsTab(TurnSnapshot snap) {
        unitsTabContent.removeAll();

        var turnHeader = new Span("Active memory units at turn %d".formatted(snap.turnNumber()));
        turnHeader.addClassName("ar-block-header");
        unitsTabContent.add(turnHeader);

        if (snap.activeUnits() == null || snap.activeUnits().isEmpty()) {
            showPlaceholder(unitsTabContent, "No active memory units at this turn.");
            return;
        }

        for (var unit : snap.activeUnits()) {
            unitsTabContent.add(unitCard(unit));
        }
    }

    private void updateDriftTab(TurnSnapshot snap) {
        driftTabContent.removeAll();

        var turnHeader = new Span("Drift verdicts at turn %d".formatted(snap.turnNumber()));
        turnHeader.addClassName("ar-block-header");
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
        summaryHeader.addClassName("ar-run-summary-header");
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

        driftTabContent.add(metricRow("Contradictions", String.valueOf(contradictions), "error"));
        driftTabContent.add(metricRow("Confirmed", String.valueOf(confirmed), "success"));
        driftTabContent.add(metricRow("Not Mentioned", String.valueOf(notMentioned), "secondary"));
        driftTabContent.add(metricRow("Resilience Rate", "%.0f%%".formatted(primaryRun.resilienceRate() * 100),
                                      primaryRun.resilienceRate() >= 0.8 ? "success" : "error"));
    }

    private void updateDiffTab(TurnSnapshot currentSnap) {
        if (diffTabContent == null) {
            return;
        }
        diffTabContent.removeAll();

        if (selectedTurnIndex <= 0) {
            showPlaceholder(diffTabContent, "Select a turn after turn 1 to see memory-unit changes from the previous turn.");
            return;
        }

        var prevSnap = primaryRun.turnSnapshots().get(selectedTurnIndex - 1);
        renderUnitDiff(diffTabContent,
                         prevSnap.activeUnits(), currentSnap.activeUnits(),
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

        var leftPanel = new VerticalLayout();
        leftPanel.setPadding(true);
        leftPanel.setSpacing(true);
        leftPanel.add(new Span("Run: %s".formatted(primaryRun.scenarioId())));
        leftPanel.addClassName("ar-run-left-panel");

        var rightPanel = new VerticalLayout();
        rightPanel.setPadding(true);
        rightPanel.setSpacing(true);
        rightPanel.add(new Span("Run: %s".formatted(compareRun.scenarioId())));

        if (primarySnap.playerMessage() != null) {
            leftPanel.add(messageBubble("Player", primarySnap.playerMessage(), "player"));
        }
        if (compareSnap.playerMessage() != null) {
            rightPanel.add(messageBubble("Player", compareSnap.playerMessage(), "player"));
        }

        if (primarySnap.dmResponse() != null) {
            leftPanel.add(messageBubble("DM", primarySnap.dmResponse(), "dm"));
        }
        if (compareSnap.dmResponse() != null) {
            rightPanel.add(messageBubble("DM", compareSnap.dmResponse(), "dm"));
        }

        if (primarySnap.verdicts() != null && !primarySnap.verdicts().isEmpty()) {
            var primaryVerdictHeader = styledLabel("Verdicts (worst: %s)".formatted(
                                                           primarySnap.worstVerdict() != null ? primarySnap.worstVerdict().verdict().name() : "none"),
                                                   "secondary");
            leftPanel.add(primaryVerdictHeader);
            for (var v : primarySnap.verdicts()) {
                leftPanel.add(verdictCard(v));
            }
        }

        if (compareSnap.verdicts() != null && !compareSnap.verdicts().isEmpty()) {
            var compareVerdictHeader = styledLabel("Verdicts (worst: %s)".formatted(
                                                           compareSnap.worstVerdict() != null ? compareSnap.worstVerdict().verdict().name() : "none"),
                                                   "secondary");
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

        comparisonTabContent.add(new H4("Unit Diff at Turn %d".formatted(primarySnap.turnNumber())));
        renderUnitDiff(comparisonTabContent,
                         primarySnap.activeUnits(), compareSnap.activeUnits(),
                         primaryRun.scenarioId(), compareRun.scenarioId());
    }

    /**
     * Render a diff between two unit lists, showing added, removed, and changed units.
     * Uses unit text (lowercased) as the diff key, and shows rank/authority changes.
     */
    private void renderUnitDiff(VerticalLayout container, List<MemoryUnit> fromUnits,
                                  List<MemoryUnit> toUnits, String fromLabel, String toLabel) {
        var from = fromUnits != null ? fromUnits : List.<MemoryUnit> of();
        var to = toUnits != null ? toUnits : List.<MemoryUnit> of();

        var fromMap = unitsByText(from);
        var toMap = unitsByText(to);

        var fromKeys = new HashSet<>(fromMap.keySet());
        var toKeys = new HashSet<>(toMap.keySet());

        var added = new HashSet<>(toKeys);
        added.removeAll(fromKeys);

        var removed = new HashSet<>(fromKeys);
        removed.removeAll(toKeys);

        var common = new HashSet<>(fromKeys);
        common.retainAll(toKeys);

        if (!added.isEmpty()) {
            var addedHeader = styledLabel("+ Added (%d)".formatted(added.size()), "success");
            container.add(addedHeader);
            for (var key : added) {
                var a = toMap.get(key);
                container.add(diffRow("[%s] %s (rank: %d)".formatted(a.authority(), a.text(), a.rank()), "added"));
            }
        }

        if (!removed.isEmpty()) {
            var removedHeader = styledLabel("- Removed (%d)".formatted(removed.size()), "error");
            container.add(removedHeader);
            for (var key : removed) {
                var a = fromMap.get(key);
                container.add(diffRow("[%s] %s (rank: %d)".formatted(a.authority(), a.text(), a.rank()), "removed"));
            }
        }

        var changed = new ArrayList<String>();
        for (var key : common) {
            var fa = fromMap.get(key);
            var ta = toMap.get(key);
            if (fa.rank() != ta.rank() || fa.authority() != ta.authority()) {
                changed.add(key);
            }
        }
        if (!changed.isEmpty()) {
            var changedHeader = styledLabel("~ Changed (%d)".formatted(changed.size()), "amber");
            container.add(changedHeader);
            for (var key : changed) {
                var fa = fromMap.get(key);
                var ta = toMap.get(key);
                var row = new VerticalLayout();
                row.setPadding(false);
                row.setSpacing(false);
                row.add(diffRow("%s: [%s] rank %d".formatted(fromLabel, fa.authority(), fa.rank()), "changed-from"));
                row.add(diffRow("%s: [%s] rank %d".formatted(toLabel, ta.authority(), ta.rank()), "changed-to"));
                var textLabel = new Span(fa.text());
                textLabel.addClassName("ar-diff-text");
                row.add(textLabel);
                container.add(row);
            }
        }

        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            showPlaceholder(container, "No memory-unit changes between %s and %s.".formatted(fromLabel, toLabel));
        }
    }

    /**
     * Index units by their lowercased text for diff comparison.
     */
    private Map<String, MemoryUnit> unitsByText(List<MemoryUnit> units) {
        var map = new HashMap<String, MemoryUnit>();
        for (var unit : units) {
            map.put(unit.text().toLowerCase(), unit);
        }
        return map;
    }

    private Div messageBubble(String role, String text, String dataRole) {
        var bubble = new Div();
        bubble.addClassName("ar-run-bubble");
        bubble.getElement().setAttribute("data-role", dataRole);

        var roleSpan = new Span(role + ": ");
        roleSpan.addClassName("ar-run-role");
        roleSpan.getElement().setAttribute("data-role", dataRole);
        bubble.add(roleSpan);
        bubble.add(new Span(text));
        return bubble;
    }

    /**
     * Rich unit card showing authority badge, text, rank bar, pinned status,
     * reinforcement count, and trust score.
     */
    private Div unitCard(MemoryUnit unit) {
        var card = new Div();
        card.addClassName("ar-card");

        var topRow = new HorizontalLayout();
        topRow.setSpacing(true);
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setWidthFull();

        var authorityBadge = new Span(unit.authority().name());
        authorityBadge.addClassName("ar-badge");
        authorityBadge.getElement().setAttribute("data-authority", unit.authority().name().toLowerCase());

        var unitText = new Span(unit.text());
        unitText.addClassName("ar-run-unit-text");

        topRow.add(authorityBadge, unitText);

        if (unit.pinned()) {
            var pinned = new Span("PINNED");
            pinned.addClassName("ar-pinned-badge");
            topRow.add(pinned);
        }

        card.add(topRow);

        var rankRow = new HorizontalLayout();
        rankRow.setSpacing(true);
        rankRow.setAlignItems(Alignment.CENTER);
        rankRow.setWidthFull();
        rankRow.addClassName("ar-run-rank-row");

        var rankLabel = new Span("Activation Score: %d".formatted(unit.rank()));
        rankLabel.addClassName("ar-rank-label");
        rankLabel.addClassName("ar-rank-label--narrow");

        var barOuter = new Div();
        barOuter.addClassName("ar-rank-bar-outer");

        var barInner = new Div();
        barInner.addClassName("ar-rank-bar-inner");
        var pct = Math.min(100, (int) (unit.rank() / 9.0));
        String barColor;
        if (unit.rank() >= 700) {
            barColor = "var(--lumo-error-color)";
        } else if (unit.rank() >= 400) {
            barColor = "#e67e22";
        } else {
            barColor = "var(--lumo-primary-color)";
        }
        barInner.getStyle()
                .set("width", pct + "%")
                .set("background", barColor);
        barOuter.add(barInner);

        rankRow.add(rankLabel, barOuter);
        card.add(rankRow);

        var bottomRow = new HorizontalLayout();
        bottomRow.setSpacing(true);
        bottomRow.addClassName("ar-run-bottom-row");

        var reinforcement = new Span("Reinforced: %dx".formatted(unit.reinforcementCount()));
        reinforcement.addClassName("ar-run-meta-text");
        bottomRow.add(reinforcement);

        if (unit.trustScore() != null) {
            var trust = new Span("Trust: %.2f".formatted(unit.trustScore().score()));
            trust.addClassName("ar-run-meta-text");
            bottomRow.add(trust);
        }

        card.add(bottomRow);
        return card;
    }

    private Div verdictCard(EvalVerdict verdict) {
        var card = new Div();
        card.addClassName("ar-verdict-card");
        card.getElement().setAttribute("data-verdict", verdictDataValue(verdict.verdict()));

        var badge = new Span(verdict.verdict().name());
        badge.addClassName("ar-run-verdict-badge-text");
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-verdict", verdictDataValue(verdict.verdict()));
        card.add(badge);

        if (verdict.factId() != null) {
            var factSpan = new Span(" [%s]".formatted(verdict.factId()));
            factSpan.addClassName("ar-run-fact-span");
            card.add(factSpan);
        }

        if (verdict.explanation() != null && !verdict.explanation().isBlank()) {
            var explanation = new Paragraph(verdict.explanation());
            explanation.addClassName("ar-run-verdict-explanation");
            card.add(explanation);
        }
        return card;
    }

    /**
     * Small colored badge for a verdict value.
     */
    private Span verdictBadge(EvalVerdict.Verdict verdict) {
        var badge = new Span(verdict.name());
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-verdict", verdictDataValue(verdict));
        return badge;
    }

    private String verdictDataValue(EvalVerdict.Verdict verdict) {
        return switch (verdict) {
            case CONTRADICTED -> "contradicted";
            case CONFIRMED -> "confirmed";
            case NOT_MENTIONED -> "not-mentioned";
        };
    }

    private HorizontalLayout metricRow(String label, String value, String colorKey) {
        var row = new HorizontalLayout();
        row.setSpacing(true);
        var labelSpan = new Span(label + ":");
        labelSpan.addClassName("ar-run-metric-label");
        var valueSpan = new Span(value);
        valueSpan.addClassName("ar-run-metric-value");
        valueSpan.getElement().setAttribute("data-color", colorKey);
        row.add(labelSpan, valueSpan);
        return row;
    }

    private Div diffRow(String text, String diffType) {
        var row = new Div();
        row.addClassName("ar-diff-row");
        row.getElement().setAttribute("data-diff", diffType);
        row.setText(text);
        return row;
    }

    private Span styledLabel(String text, String colorKey) {
        var label = new Span(text);
        label.addClassName("ar-styled-label");
        label.getElement().setAttribute("data-color", colorKey);
        return label;
    }

    private void showPlaceholder(VerticalLayout container, String message) {
        var placeholder = new Paragraph(message);
        placeholder.addClassName("ar-placeholder");
        container.add(placeholder);
    }
}
