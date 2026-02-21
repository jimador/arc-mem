package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimControlState;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Root Vaadin view for running and inspecting anchor drift simulations.
 * <p>
 * Layout (per design D1):
 * <pre>
 *   +-----------------------------------------------------------+
 *   |  Header: title + scenario selector + controls             |
 *   +-----------------------------------------------------------+
 *   |  InterventionImpactBanner (shown after resume)            |
 *   +------------------------------+----------------------------+
 *   |  Left Column:                |  Right Column (TabSheet):  |
 *   |  - ConversationPanel         |  - Context Inspector       |
 *   |  - DriftSummaryPanel         |  - Anchor Timeline         |
 *   |                              |  - Anchor Manipulation     |
 *   |                              |  - Knowledge Browser       |
 *   +------------------------------+----------------------------+
 *   |  Status bar + progress bar                                |
 *   +-----------------------------------------------------------+
 * </pre>
 * <p>
 * All panel visibility and enable states are driven by {@link SimControlState}
 * transitions via {@link #transitionTo(SimControlState)}.
 */
@Route("")
@PageTitle("Anchor Drift Simulator")
public class SimulationView extends VerticalLayout {

    private final SimulationService simulationService;
    private final ScenarioLoader scenarioLoader;
    private final AnchorRepository anchorRepository;
    private final AnchorEngine anchorEngine;

    // Controls
    private final ComboBox<SimulationScenario> scenarioCombo;
    private final Checkbox injectionToggle;
    private final IntegerField tokenBudgetField;
    private final IntegerField maxTurnsField;
    private final Button runButton;
    private final Button pauseButton;
    private final Button resumeButton;
    private final Button stopButton;
    private final Button runHistoryButton;
    private final Details scenarioBriefPanel;
    private final Span scenarioBriefTitle;
    private final Paragraph scenarioBriefObjective;
    private final Paragraph scenarioBriefFocus;
    private final VerticalLayout scenarioBriefHighlights;
    private final Paragraph scenarioBriefSetting;

    // Progress
    private final ProgressBar progressBar;
    private final Span statusLabel;

    // Panels
    private final ConversationPanel conversationPanel;
    private final ContextInspectorPanel inspectorPanel;
    private final DriftSummaryPanel driftSummaryPanel;
    private final AnchorManipulationPanel manipulationPanel;
    private final AnchorTimelinePanel timelinePanel;
    private final InterventionImpactBanner interventionBanner;
    private final KnowledgeBrowserPanel knowledgeBrowserPanel;

    // TabSheet and manipulation tab reference for visibility control
    private final TabSheet rightTabSheet;
    private final Tab manipulationTab;
    private final Tab knowledgeBrowserTab;
    private final RunHistoryDialog runHistoryDialog;
    private final ProgressDispatcher dispatcher;

    // Theme toggle
    private final Button themeToggleButton;
    private String currentTheme = "dark";

    // State
    private SimControlState controlState = SimControlState.IDLE;
    private int anchorCountBeforePause;

    public SimulationView(
            SimulationService simulationService,
            ScenarioLoader scenarioLoader,
            AnchorRepository anchorRepository,
            AnchorEngine anchorEngine) {
        this.simulationService = simulationService;
        this.scenarioLoader = scenarioLoader;
        this.anchorRepository = anchorRepository;
        this.anchorEngine = anchorEngine;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        // --- Header ---
        var title = new H2("Anchor Drift Simulator");
        title.addClassName("ar-sim-title");

        themeToggleButton = new Button("\u2600 LIGHT");
        themeToggleButton.addClickListener(e -> toggleTheme());

        var headerRow = new HorizontalLayout(title, themeToggleButton);
        headerRow.setWidthFull();
        headerRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerRow.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        headerRow.addClassName("ar-header-row");

        scenarioCombo = new ComboBox<>("Scenario");
        scenarioCombo.setItemLabelGenerator(s -> {
            var label = s.displayTitle() + (s.adversarial() ? " [adversarial]" : " [baseline]");
            if (s.category() != null && !s.category().isBlank()) {
                label = "[%s] %s".formatted(s.category(), label);
            }
            return label;
        });
        scenarioCombo.setWidth("280px");
        scenarioCombo.setPlaceholder("Select a scenario...");

        injectionToggle = new Checkbox("Anchor Injection", true);
        injectionToggle.addClassName("ar-inject-toggle");

        tokenBudgetField = new IntegerField("Token Budget (0=off)");
        tokenBudgetField.setMin(0);
        tokenBudgetField.setMax(5000);
        tokenBudgetField.setStepButtonsVisible(true);
        tokenBudgetField.setValue(0);
        tokenBudgetField.setWidth("190px");

        maxTurnsField = new IntegerField("Max Turns");
        maxTurnsField.setMin(1);
        maxTurnsField.setMax(200);
        maxTurnsField.setStepButtonsVisible(true);
        maxTurnsField.setValue(10);
        maxTurnsField.setWidth("140px");

        runButton = new Button("Run");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.setEnabled(false);

        pauseButton = new Button("Pause");
        pauseButton.setEnabled(false);

        resumeButton = new Button("Resume");
        resumeButton.setEnabled(false);

        stopButton = new Button("Stop");
        stopButton.setEnabled(false);

        runHistoryDialog = new RunHistoryDialog(simulationService.getRunStore());

        runHistoryButton = new Button("Run History");
        runHistoryButton.addClickListener(e -> {
            runHistoryDialog.refreshGrid();
            runHistoryDialog.open();
        });

        wireButtons();

        var controls = new HorizontalLayout(
                scenarioCombo, injectionToggle, tokenBudgetField, maxTurnsField, runButton, pauseButton, resumeButton, stopButton, runHistoryButton);
        controls.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        controls.setSpacing(true);

        // --- Scenario brief ---
        scenarioBriefTitle = new Span("Scenario Brief");
        scenarioBriefTitle.addClassName("ar-scenario-brief-title");

        scenarioBriefObjective = new Paragraph("Select a scenario to view what it tests.");
        scenarioBriefObjective.addClassName("ar-no-margin");

        scenarioBriefFocus = new Paragraph("");
        scenarioBriefFocus.addClassName("ar-scenario-brief-focus");

        scenarioBriefHighlights = new VerticalLayout();
        scenarioBriefHighlights.setPadding(false);
        scenarioBriefHighlights.setSpacing(false);
        scenarioBriefHighlights.addClassName("ar-brief-highlights");

        scenarioBriefSetting = new Paragraph("");
        scenarioBriefSetting.addClassName("ar-scenario-brief-setting");

        var briefContent = new VerticalLayout(
                scenarioBriefObjective,
                scenarioBriefFocus,
                scenarioBriefHighlights,
                scenarioBriefSetting);
        briefContent.setPadding(false);
        briefContent.setSpacing(false);

        scenarioBriefPanel = new Details(scenarioBriefTitle, briefContent);
        scenarioBriefPanel.setOpened(true);
        scenarioBriefPanel.addClassName("ar-scenario-brief");

        // --- Intervention impact banner ---
        interventionBanner = new InterventionImpactBanner();

        // --- Left column: Conversation + DriftSummary ---
        conversationPanel = new ConversationPanel();
        conversationPanel.setTurnSelectionCallback(this::onTurnSelected);

        driftSummaryPanel = new DriftSummaryPanel();

        var leftColumn = new VerticalLayout();
        leftColumn.setPadding(false);
        leftColumn.setSpacing(false);
        leftColumn.setSizeFull();
        leftColumn.add(new H4("Conversation"), conversationPanel, driftSummaryPanel);
        leftColumn.setFlexGrow(1, conversationPanel);

        // --- Right column: TabSheet with panels ---
        inspectorPanel = new ContextInspectorPanel();
        timelinePanel = new AnchorTimelinePanel();
        manipulationPanel = new AnchorManipulationPanel(anchorRepository, anchorEngine);
        knowledgeBrowserPanel = new KnowledgeBrowserPanel(anchorEngine, anchorRepository);

        // Wire cross-panel turn selection (10.7)
        timelinePanel.setTurnSelectionListener(this::onTurnSelected);

        // Wire progress dispatcher
        dispatcher = new ProgressDispatcher();
        dispatcher.addListener(conversationPanel);
        dispatcher.addListener(driftSummaryPanel);
        dispatcher.addListener(inspectorPanel);
        dispatcher.addListener(timelinePanel);
        dispatcher.addListener(knowledgeBrowserPanel);

        rightTabSheet = new TabSheet();
        rightTabSheet.setSizeFull();
        rightTabSheet.add("Context Inspector", inspectorPanel);
        rightTabSheet.add("Timeline", timelinePanel);
        manipulationTab = rightTabSheet.add("Manipulation", manipulationPanel);
        knowledgeBrowserTab = rightTabSheet.add("Knowledge Browser", knowledgeBrowserPanel);

        // Manipulation tab visible only when PAUSED
        manipulationTab.setVisible(false);

        // Wire cross-panel browse linking (13.3): anchor Browse -> Knowledge Browser filter
        inspectorPanel.setBrowseCallback(anchorText -> {
            knowledgeBrowserPanel.filterByAnchorText(anchorText);
            // Auto-switch to Knowledge Browser tab
            rightTabSheet.setSelectedTab(knowledgeBrowserTab);
        });
        inspectorPanel.setBrowseGraphCallback(anchorText -> {
            knowledgeBrowserPanel.focusGraphForAnchorText(anchorText);
            rightTabSheet.setSelectedTab(knowledgeBrowserTab);
        });

        var rightWrapper = new VerticalLayout();
        rightWrapper.setPadding(false);
        rightWrapper.setSpacing(false);
        rightWrapper.setSizeFull();
        rightWrapper.add(rightTabSheet);

        // --- Split layout ---
        var splitLayout = new SplitLayout(leftColumn, rightWrapper);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(55);

        // --- Status bar ---
        statusLabel = new Span("Select a scenario and click Run.");
        statusLabel.addClassName("ar-status-label");

        progressBar = new ProgressBar(0, 1, 0);
        progressBar.setWidthFull();
        progressBar.setVisible(false);

        var statusBar = new VerticalLayout(progressBar, statusLabel);
        statusBar.setPadding(false);
        statusBar.setSpacing(false);
        statusBar.setWidthFull();
        statusBar.addClassName("ar-status-bar");

        // --- Assembly ---
        add(headerRow, controls, scenarioBriefPanel, interventionBanner, splitLayout, statusBar);
        setFlexGrow(1, splitLayout);
        updateScenarioContext(null);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        loadScenarios();
        initTheme();
    }

    // -------------------------------------------------------------------------
    // State machine (10.9)
    // -------------------------------------------------------------------------

    /**
     * Transition the UI to a new control state, applying all visibility and enable
     * changes dictated by the state machine.
     */
    private void transitionTo(SimControlState target) {
        if (!controlState.canTransitionTo(target)) {
            return;
        }
        controlState = target;

        switch (target) {
            case IDLE -> {
                scenarioCombo.setEnabled(true);
                tokenBudgetField.setEnabled(true);
                runButton.setEnabled(scenarioCombo.getValue() != null);
                pauseButton.setEnabled(false);
                resumeButton.setEnabled(false);
                stopButton.setEnabled(false);
                progressBar.setVisible(false);
                manipulationTab.setVisible(false);
            }
            case RUNNING -> {
                scenarioCombo.setEnabled(false);
                tokenBudgetField.setEnabled(false);
                runButton.setEnabled(false);
                pauseButton.setEnabled(true);
                resumeButton.setEnabled(false);
                stopButton.setEnabled(true);
                manipulationTab.setVisible(false);
                interventionBanner.dismiss();
            }
            case PAUSED -> {
                scenarioCombo.setEnabled(false);
                pauseButton.setEnabled(false);
                resumeButton.setEnabled(true);
                stopButton.setEnabled(true);
                manipulationTab.setVisible(true);

                // Load anchors into manipulation panel
                var contextId = simulationService.getCurrentContextId();
                if (contextId != null) {
                    anchorCountBeforePause = anchorEngine.activeCount(contextId);
                    manipulationPanel.loadAnchors(contextId);
                }

                // Auto-switch to Manipulation tab
                rightTabSheet.setSelectedTab(manipulationTab);
            }
            case COMPLETED -> {
                scenarioCombo.setEnabled(true);
                tokenBudgetField.setEnabled(true);
                runButton.setEnabled(scenarioCombo.getValue() != null);
                pauseButton.setEnabled(false);
                resumeButton.setEnabled(false);
                stopButton.setEnabled(false);
                progressBar.setVisible(false);
                manipulationTab.setVisible(false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wiring
    // -------------------------------------------------------------------------

    private void wireButtons() {
        scenarioCombo.addValueChangeListener(e -> {
            runButton.setEnabled(e.getValue() != null && canStartRun());
            updateScenarioContext(e.getValue());
            if (e.getValue() != null) {
                maxTurnsField.setValue(e.getValue().maxTurns());
            }
        });

        runButton.addClickListener(e -> {
            var scenario = scenarioCombo.getValue();
            if (scenario == null) {
                return;
            }
            startSimulation(scenario);
        });

        pauseButton.addClickListener(e -> {
            simulationService.pause();
            transitionTo(SimControlState.PAUSED);
            statusLabel.setText("Paused.");
        });

        resumeButton.addClickListener(e -> {
            simulationService.resume();

            // Show intervention impact banner (10.8)
            var contextId = simulationService.getCurrentContextId();
            if (contextId != null) {
                int currentCount = anchorEngine.activeCount(contextId);
                int delta = currentCount - anchorCountBeforePause;
                int interventionCount = manipulationPanel.getInterventionCount();
                if (interventionCount > 0) {
                    interventionBanner.show(interventionCount, delta);
                }
            }

            transitionTo(SimControlState.RUNNING);
            statusLabel.setText("Resumed.");
        });

        stopButton.addClickListener(e -> {
            simulationService.cancel();
            transitionTo(SimControlState.COMPLETED);
            statusLabel.setText("Stopping...");
        });
    }

    private void loadScenarios() {
        try {
            var scenarios = scenarioLoader.listScenarios();

            // Group scenarios by category if categories exist (13.3)
            var hasCategories = scenarios.stream()
                                         .anyMatch(s -> s.category() != null && !s.category().isBlank());
            if (hasCategories) {
                var grouped = scenarios.stream()
                                       .sorted((a, b) -> {
                                           var catA = a.category() != null ? a.category() : "uncategorized";
                                           var catB = b.category() != null ? b.category() : "uncategorized";
                                           var cmp = catA.compareTo(catB);
                                           return cmp != 0 ? cmp : a.id().compareTo(b.id());
                                       })
                                       .toList();
                scenarioCombo.setItems(grouped);
            } else {
                scenarioCombo.setItems(scenarios);
            }

            if (!scenarios.isEmpty()) {
                scenarioCombo.setValue(scenarios.getFirst());
            }
        } catch (Exception e) {
            statusLabel.setText("Failed to load scenarios: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Simulation lifecycle
    // -------------------------------------------------------------------------

    private void startSimulation(SimulationScenario scenario) {
        var maxTurns = maxTurnsField.getValue() != null ? Math.max(1, maxTurnsField.getValue()) : scenario.maxTurns();
        transitionTo(SimControlState.RUNNING);
        conversationPanel.reset();
        conversationPanel.appendSystemMessage(buildScenarioRunIntro(scenario));
        inspectorPanel.reset();
        driftSummaryPanel.reset();
        timelinePanel.reset();
        manipulationPanel.reset();
        knowledgeBrowserPanel.reset();
        interventionBanner.dismiss();

        // Set seed anchor count for survival rate calculation
        var seedCount = scenario.seedAnchors() != null ? scenario.seedAnchors().size() : 0;
        driftSummaryPanel.setSeedAnchorCount(seedCount);

        progressBar.setVisible(true);
        progressBar.setValue(0);
        statusLabel.setText("Starting simulation: " + scenario.id());

        var ui = UI.getCurrent();

        CompletableFuture.runAsync(() ->
                                           simulationService.runSimulation(scenario, maxTurns, injectionToggle::getValue, this::resolveTokenBudget,
                                                                           progress -> ui.access(() -> applyProgress(progress, scenario)))
        ).exceptionally(ex -> {
            ui.access(() -> {
                statusLabel.setText("Simulation error: " + ex.getMessage());
                transitionTo(SimControlState.COMPLETED);
            });
            return null;
        });
    }

    private void applyProgress(SimulationProgress progress, SimulationScenario scenario) {
        if (scenario.maxTurns() > 0) {
            progressBar.setValue((double) progress.turnNumber() / scenario.maxTurns());
        }
        statusLabel.setText(progress.statusMessage());
        interventionBanner.dismiss();

        // Lazy contextId init for KnowledgeBrowserPanel
        if (knowledgeBrowserPanel.getContextId() == null) {
            knowledgeBrowserPanel.setContextId(simulationService.getCurrentContextId());
        }

        dispatcher.dispatch(progress);

        if (progress.complete()) {
            progressBar.setValue(1.0);
            transitionTo(SimControlState.COMPLETED);
        }
    }

    // -------------------------------------------------------------------------
    // Cross-panel turn selection (10.7)
    // -------------------------------------------------------------------------

    /**
     * Handle turn selection from any panel (ConversationPanel, AnchorTimelinePanel).
     * Updates all panels that support turn-based inspection.
     */
    private void onTurnSelected(int turnNumber) {
        // Highlight in timeline
        timelinePanel.selectTurn(turnNumber);

        // ContextInspectorPanel shows latest state; historical turn inspection
        // would require storing per-turn context traces (future enhancement).
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Integer resolveTokenBudget() {
        var value = tokenBudgetField.getValue();
        return value != null ? Math.max(0, value) : 0;
    }

    private boolean canStartRun() {
        return controlState == SimControlState.IDLE || controlState == SimControlState.COMPLETED;
    }

    private void updateScenarioContext(@Nullable SimulationScenario scenario) {
        scenarioBriefHighlights.removeAll();
        if (scenario == null) {
            scenarioBriefTitle.setText("Scenario Brief");
            scenarioBriefObjective.setText("Select a scenario to view what it tests.");
            scenarioBriefFocus.setText("");
            scenarioBriefSetting.setText("");
            return;
        }

        scenarioBriefTitle.setText(scenario.displayTitle());
        scenarioBriefObjective.setText("Test objective: " + scenario.displayObjective());
        scenarioBriefFocus.setText("Focus: " + scenario.displayTestFocus());

        for (var highlight : scenario.displayHighlights()) {
            var line = new Span("- " + highlight);
            line.addClassName("ar-scenario-highlight");
            scenarioBriefHighlights.add(line);
        }

        var settingPreview = summarizeSetting(scenario.setting());
        scenarioBriefSetting.setText("Setting: " + settingPreview);
    }

    private String buildScenarioRunIntro(SimulationScenario scenario) {
        var details = scenario.displayHighlights().isEmpty()
                ? "No additional details provided."
                : String.join("; ", scenario.displayHighlights());
        return "Scenario: %s\nObjective: %s\nFocus: %s\nDetails: %s".formatted(
                scenario.displayTitle(),
                scenario.displayObjective(),
                scenario.displayTestFocus(),
                details);
    }

    private String summarizeSetting(@Nullable String setting) {
        if (setting == null || setting.isBlank()) {
            return "No setting details provided.";
        }
        var normalized = setting.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 220) + "...";
    }

    // -------------------------------------------------------------------------
    // Turn timing
    // -------------------------------------------------------------------------

    /**
     * Format a turn duration in milliseconds to a human-readable string.
     * Returns "Xs", "XXs", or "Xm Ys" depending on magnitude.
     */
    static String formatDuration(long ms) {
        if (ms <= 0) {
            return "";
        }
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }

    // -------------------------------------------------------------------------
    // Theme toggle
    // -------------------------------------------------------------------------

    private void initTheme() {
        UI.getCurrent().getPage().executeJs("""
                const t = localStorage.getItem('anchor-theme');
                if (t === 'light') document.documentElement.setAttribute('theme', 'light');
                return t || 'dark';
                """).then(String.class, theme -> {
            currentTheme = theme;
            updateThemeButton();
        });
    }

    private void toggleTheme() {
        currentTheme = "dark".equals(currentTheme) ? "light" : "dark";
        if ("light".equals(currentTheme)) {
            UI.getCurrent().getPage().executeJs(
                    "document.documentElement.setAttribute('theme','light');" +
                    "localStorage.setItem('anchor-theme','light')");
        } else {
            UI.getCurrent().getPage().executeJs(
                    "document.documentElement.removeAttribute('theme');" +
                    "localStorage.setItem('anchor-theme','dark')");
        }
        updateThemeButton();
    }

    private void updateThemeButton() {
        themeToggleButton.setText("dark".equals(currentTheme) ? "\u2600 LIGHT" : "\uD83C\uDF19 DARK");
    }

}
