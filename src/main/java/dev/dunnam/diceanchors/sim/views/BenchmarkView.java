package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import dev.dunnam.diceanchors.chat.ChatView;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentRunner;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.report.ResilienceReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Root Vaadin view for running and inspecting anchor benchmarks.
 * <p>
 * View state is driven by {@link BenchmarkViewState} transitions
 * via {@link #transitionTo(BenchmarkViewState)}.
 * <p>
 * State &rarr; panel visibility mapping (design D2):
 * <ul>
 *   <li><strong>CONFIG</strong>: ExperimentConfigPanel + ExperimentHistoryPanel</li>
 *   <li><strong>RUNNING</strong>: ExperimentProgressPanel only</li>
 *   <li><strong>RESULTS</strong>: ConditionComparisonPanel (with inline drill-down) + ExperimentHistoryPanel</li>
 * </ul>
 */
@Route("benchmark")
@PageTitle("Anchor Benchmarks")
public class BenchmarkView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkView.class);

    private final ExperimentRunner experimentRunner;
    private final RunHistoryStore runHistoryStore;

    // --- Panels ---
    private final ExperimentConfigPanel configPanel;
    private final ExperimentProgressPanel progressPanel;
    private final ConditionComparisonPanel comparisonPanel;
    private final FactDrillDownPanel drillDownPanel;
    private final ExperimentHistoryPanel historyPanel;
    private final Div errorBanner;

    private BenchmarkViewState viewState = BenchmarkViewState.CONFIG;

    public BenchmarkView(
            ExperimentRunner experimentRunner,
            ScenarioLoader scenarioLoader,
            RunHistoryStore runHistoryStore,
            ResilienceReportBuilder reportBuilder) {
        this.experimentRunner = experimentRunner;
        this.runHistoryStore = runHistoryStore;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // --- Header ---
        var title = new H2("Anchor Benchmarks");
        title.addClassName("ar-sim-title");

        var simLink = new RouterLink("Simulator", SimulationView.class);
        simLink.addClassName("ar-nav-link");
        var chatLink = new RouterLink("Chat", ChatView.class);
        chatLink.addClassName("ar-nav-link");

        var headerRow = new HorizontalLayout(title, simLink, chatLink);
        headerRow.setWidthFull();
        headerRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerRow.addClassName("ar-header-row");

        // --- Error banner ---
        errorBanner = new Div();
        errorBanner.addClassName("ar-cancelled-banner");
        errorBanner.setVisible(false);

        // --- Panels ---
        configPanel = new ExperimentConfigPanel(scenarioLoader.listScenarios());
        progressPanel = new ExperimentProgressPanel();
        drillDownPanel = new FactDrillDownPanel(runHistoryStore, scenarioLoader);
        comparisonPanel = new ConditionComparisonPanel();
        comparisonPanel.setDrillDownPanel(drillDownPanel);
        comparisonPanel.setReportBuilder(reportBuilder);
        historyPanel = new ExperimentHistoryPanel(runHistoryStore);

        // --- Wire callbacks (tasks 8.1, 8.2, 8.3) ---
        configPanel.setOnRunExperiment(this::onRunExperiment);
        progressPanel.setCancelCallback(this::onCancelExperiment);
        historyPanel.setLoadCallback(this::onLoadExperiment);
        historyPanel.setDeleteCallback(this::onDeleteExperiment);

        // --- Layout ---
        var content = new VerticalLayout(
                errorBanner,
                configPanel,
                progressPanel,
                comparisonPanel,
                historyPanel
        );
        content.setSizeFull();
        content.setPadding(true);
        content.setSpacing(true);

        add(headerRow, content);

        // Initial state: CONFIG
        historyPanel.refresh();
        applyPanelVisibility();
    }

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    /**
     * Transition the view to {@code target} if the current state allows it.
     * Controls panel visibility per design D2.
     *
     * @param target the desired next state
     */
    private void transitionTo(BenchmarkViewState target) {
        if (!viewState.canTransitionTo(target)) {
            logger.warn("Ignoring invalid BenchmarkView transition {} -> {}", viewState, target);
            return;
        }
        logger.debug("BenchmarkView transition {} -> {}", viewState, target);
        viewState = target;
        applyPanelVisibility();
    }

    /**
     * Show/hide panels based on the current {@link #viewState}.
     */
    private void applyPanelVisibility() {
        errorBanner.setVisible(false);

        switch (viewState) {
            case CONFIG -> {
                configPanel.setVisible(true);
                progressPanel.setVisible(false);
                comparisonPanel.setVisible(false);
                historyPanel.setVisible(true);
            }
            case RUNNING -> {
                configPanel.setVisible(false);
                progressPanel.setVisible(true);
                comparisonPanel.setVisible(false);
                historyPanel.setVisible(false);
            }
            case RESULTS -> {
                configPanel.setVisible(false);
                progressPanel.setVisible(false);
                comparisonPanel.setVisible(true);
                historyPanel.setVisible(true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Task 8.1: Run experiment async
    // -------------------------------------------------------------------------

    private void onRunExperiment(dev.dunnam.diceanchors.sim.benchmark.ExperimentDefinition definition) {
        transitionTo(BenchmarkViewState.RUNNING);
        progressPanel.reset();

        var ui = UI.getCurrent();

        CompletableFuture.supplyAsync(() ->
                experimentRunner.runExperiment(
                        definition,
                        () -> true,   // injection enabled by default
                        () -> 4096,   // default token budget
                        progress -> ui.access(() -> progressPanel.updateProgress(progress)))
        ).thenAccept(report ->
                ui.access(() -> showResults(report))
        ).exceptionally(ex -> {
            ui.access(() -> showError(ex));
            return null;
        });
    }

    private void showResults(ExperimentReport report) {
        transitionTo(BenchmarkViewState.RESULTS);
        comparisonPanel.showReport(report);
        drillDownPanel.showReport(report);
        historyPanel.refresh();
    }

    private void showError(Throwable ex) {
        logger.error("Experiment execution failed", ex);
        transitionTo(BenchmarkViewState.CONFIG);
        errorBanner.setText("Experiment failed: " + ex.getMessage());
        errorBanner.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Task 8.2: Cancel experiment
    // -------------------------------------------------------------------------

    private void onCancelExperiment() {
        experimentRunner.cancel();
        // The experiment will complete the current cell and return a report with cancelled=true.
        // showResults() will handle the cancelled banner via ConditionComparisonPanel.
    }

    // -------------------------------------------------------------------------
    // Task 8.3: Load experiment from history
    // -------------------------------------------------------------------------

    private void onLoadExperiment(ExperimentReport report) {
        showResults(report);
    }

    private void onDeleteExperiment(String reportId) {
        runHistoryStore.deleteExperimentReport(reportId);
        historyPanel.refresh();
    }
}
