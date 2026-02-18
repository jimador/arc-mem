package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimControlState;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.jspecify.annotations.Nullable;

import java.util.List;
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
    private final Button runButton;
    private final Button pauseButton;
    private final Button resumeButton;
    private final Button stopButton;
    private final Button runHistoryButton;
    private final VerticalLayout scenarioBriefPanel;
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
        title.getStyle().set("margin", "0 0 12px 0");

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
        injectionToggle.getStyle().set("align-self", "flex-end");

        tokenBudgetField = new IntegerField("Token Budget (0=off)");
        tokenBudgetField.setMin(0);
        tokenBudgetField.setMax(5000);
        tokenBudgetField.setStepButtonsVisible(true);
        tokenBudgetField.setValue(0);
        tokenBudgetField.setWidth("190px");

        runButton = new Button("Run");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.setEnabled(false);

        pauseButton = new Button("Pause");
        pauseButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        pauseButton.setEnabled(false);

        resumeButton = new Button("Resume");
        resumeButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        resumeButton.setEnabled(false);

        stopButton = new Button("Stop");
        stopButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        stopButton.setEnabled(false);

        runHistoryDialog = new RunHistoryDialog(simulationService.getRunStore());

        runHistoryButton = new Button("Run History");
        runHistoryButton.addClickListener(e -> {
            runHistoryDialog.refreshGrid();
            runHistoryDialog.open();
        });

        wireButtons();

        var controls = new HorizontalLayout(
                scenarioCombo, injectionToggle, tokenBudgetField, runButton, pauseButton, resumeButton, stopButton, runHistoryButton);
        controls.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        controls.setSpacing(true);

        // --- Scenario brief ---
        scenarioBriefTitle = new Span("Scenario Brief");
        scenarioBriefTitle.getStyle()
                          .set("font-size", "var(--lumo-font-size-m)")
                          .set("font-weight", "700");

        scenarioBriefObjective = new Paragraph("Select a scenario to view what it tests.");
        scenarioBriefObjective.getStyle().set("margin", "0");

        scenarioBriefFocus = new Paragraph("");
        scenarioBriefFocus.getStyle()
                          .set("margin", "0")
                          .set("font-size", "var(--lumo-font-size-s)")
                          .set("color", "var(--lumo-secondary-text-color)");

        scenarioBriefHighlights = new VerticalLayout();
        scenarioBriefHighlights.setPadding(false);
        scenarioBriefHighlights.setSpacing(false);
        scenarioBriefHighlights.getStyle()
                              .set("margin", "0")
                              .set("gap", "2px");

        scenarioBriefSetting = new Paragraph("");
        scenarioBriefSetting.getStyle()
                            .set("margin", "0")
                            .set("font-size", "var(--lumo-font-size-s)");

        scenarioBriefPanel = new VerticalLayout(
                scenarioBriefTitle,
                scenarioBriefObjective,
                scenarioBriefFocus,
                scenarioBriefHighlights,
                scenarioBriefSetting);
        scenarioBriefPanel.setPadding(true);
        scenarioBriefPanel.setSpacing(false);
        scenarioBriefPanel.getStyle()
                          .set("border", "1px solid var(--lumo-contrast-10pct)")
                          .set("border-radius", "8px")
                          .set("background", "var(--lumo-base-color)")
                          .set("margin", "8px 0");

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
        statusLabel.getStyle()
                   .set("font-size", "var(--lumo-font-size-s)")
                   .set("color", "var(--lumo-secondary-text-color)");

        progressBar = new ProgressBar(0, 1, 0);
        progressBar.setWidthFull();
        progressBar.setVisible(false);

        var statusBar = new VerticalLayout(progressBar, statusLabel);
        statusBar.setPadding(false);
        statusBar.setSpacing(false);
        statusBar.setWidthFull();
        statusBar.getStyle().set("margin-top", "8px");

        // --- Assembly ---
        add(title, controls, scenarioBriefPanel, interventionBanner, splitLayout, statusBar);
        setFlexGrow(1, splitLayout);
        updateScenarioContext(null);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        loadScenarios();
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
                                           simulationService.runSimulation(scenario, injectionToggle::getValue, this::resolveTokenBudget,
                                                                           progress -> ui.access(() -> applyProgress(progress, scenario)))
        ).exceptionally(ex -> {
            ui.access(() -> {
                statusLabel.setText("Simulation error: " + ex.getMessage());
                transitionTo(SimControlState.COMPLETED);
            });
            return null;
        });
    }

    /**
     * Dispatch progress updates to all sub-panels via direct method calls.
     */
    private void applyProgress(SimulationProgress progress, SimulationScenario scenario) {
        // Update progress bar
        if (scenario.maxTurns() > 0) {
            progressBar.setValue((double) progress.turnNumber() / scenario.maxTurns());
        }

        // Update status label
        statusLabel.setText(progress.statusMessage());

        // Dismiss intervention banner on next turn
        interventionBanner.dismiss();

        // Dispatch to ConversationPanel
        if (progress.lastPlayerMessage() != null || progress.lastDmResponse() != null) {
            conversationPanel.appendTurn(progress);
        }

        if (knowledgeBrowserPanel.getContextId() == null) {
            knowledgeBrowserPanel.setContextId(simulationService.getCurrentContextId());
        } else if (progress.contextTrace() != null) {
            knowledgeBrowserPanel.refresh();
        }

        // Track verdicts for DriftSummaryPanel
        if (progress.verdicts() != null && !progress.verdicts().isEmpty()) {
            driftSummaryPanel.recordTurnVerdicts(progress.turnNumber(), progress.verdicts());
        }

        // Update ContextInspectorPanel
        if (progress.contextTrace() != null) {
            var verdicts = progress.verdicts() != null ? progress.verdicts() : List.<EvalVerdict> of();
            inspectorPanel.update(progress.contextTrace(), verdicts);
        }

        // Update AnchorTimelinePanel progressively
        timelinePanel.appendTurn(progress);

        // Feed anchor lifecycle events to timeline
        if (progress.anchorEvents() != null && !progress.anchorEvents().isEmpty()) {
            timelinePanel.appendAnchorEvents(progress.turnNumber(), progress.anchorEvents());
        }

        // Wire compaction data to ContextInspectorPanel
        if (progress.compactionResult() != null) {
            var cr = progress.compactionResult();
            inspectorPanel.updateCompaction(
                    cr.triggerReason(),
                    cr.tokensBefore() - cr.tokensAfter(),
                    cr.protectedContentIds(),
                    cr.summary(),
                    cr.durationMs(),
                    cr.lossEvents());
        }

        // Handle terminal states
        if (progress.complete()) {
            progressBar.setValue(1.0);

            if (progress.phase() == SimulationProgress.SimulationPhase.CANCELLED) {
                conversationPanel.appendSystemMessage("Simulation cancelled.");
                transitionTo(SimControlState.COMPLETED);
            } else {
                conversationPanel.appendSystemMessage("Simulation complete! Final anchor count: "
                                                      + progress.activeAnchors().size());

                // Show drift summary with final results
                driftSummaryPanel.showResults(progress);
                transitionTo(SimControlState.COMPLETED);
            }
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
            line.getStyle().set("font-size", "var(--lumo-font-size-s)");
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

}
