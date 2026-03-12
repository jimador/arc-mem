package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.AnchorMutationStrategy;
import dev.dunnam.diceanchors.chat.ChatView;
import dev.dunnam.diceanchors.chat.SimToChatBridge;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimControlState;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Route("")
@PageTitle("Anchor Drift Simulator")
public class SimulationView extends VerticalLayout {

    private final SimulationService simulationService;
    private final ScenarioLoader scenarioLoader;
    private final AnchorEngine anchorEngine;

    private final Select<String> categorySelect;
    private final ComboBox<SimulationScenario> scenarioCombo;
    private final Checkbox injectionToggle;
    private final IntegerField tokenBudgetField;
    private final IntegerField maxTurnsField;
    private final Button runButton;
    private final Button pauseButton;
    private final Button resumeButton;
    private final Button stopButton;
    private final Button runHistoryButton;

    private final VerticalLayout scenarioDetailsContent;
    private final Span scenarioDetailsTitle;
    private final Paragraph scenarioDetailsObjective;
    private final Paragraph scenarioDetailsFocus;
    private final VerticalLayout scenarioDetailsHighlights;
    private final Paragraph scenarioDetailsSetting;

    private final ProgressBar progressBar;
    private final Span statusLabel;

    private final ConversationPanel conversationPanel;
    private final ContextInspectorPanel inspectorPanel;
    private final DriftSummaryPanel driftSummaryPanel;
    private final AnchorManipulationPanel manipulationPanel;
    private final AnchorTimelinePanel timelinePanel;
    private final InterventionImpactBanner interventionBanner;
    private final KnowledgeBrowserPanel knowledgeBrowserPanel;

    private final TabSheet rightTabSheet;
    private final Tab scenarioDetailsTab;
    private final Tab contextInspectorTab;
    private final Tab manipulationTab;
    private final Tab knowledgeBrowserTab;
    private final Tab resultsTab;
    private final Tab runHistoryTab;
    private final RunHistoryPanel runHistoryPanel;
    private final ProgressDispatcher dispatcher;

    private final Button themeToggleButton;
    private String currentTheme = "dark";

    private SimControlState controlState = SimControlState.IDLE;
    private int anchorCountBeforePause;
    private TreeMap<String, List<SimulationScenario>> scenariosByCategory = new TreeMap<>();

    public SimulationView(
            SimulationService simulationService,
            ScenarioLoader scenarioLoader,
            AnchorRepository anchorRepository,
            AnchorEngine anchorEngine,
            RunHistoryStore runHistoryStore,
            AnchorMutationStrategy mutationStrategy,
            SimToChatBridge simToChatBridge) {
        this.simulationService = simulationService;
        this.scenarioLoader = scenarioLoader;
        this.anchorEngine = anchorEngine;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        // --- Header row: title left, nav links + theme toggle right ---
        var title = new H2("Anchor Drift Simulator");
        title.addClassName("ar-sim-title");

        var chatLink = new RouterLink("Chat", ChatView.class);
        chatLink.addClassName("ar-nav-link");

        var benchmarkLink = new RouterLink("Benchmark", BenchmarkView.class);
        benchmarkLink.addClassName("ar-nav-link");

        themeToggleButton = new Button("\u2600 LIGHT");
        themeToggleButton.addClickListener(e -> toggleTheme());

        var navGroup = new HorizontalLayout(chatLink, benchmarkLink, themeToggleButton);
        navGroup.setSpacing(true);
        navGroup.setAlignItems(HorizontalLayout.Alignment.CENTER);

        var headerRow = new HorizontalLayout(title, navGroup);
        headerRow.setWidthFull();
        headerRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerRow.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        headerRow.addClassName("ar-header-row");

        // --- Controls: single row ---
        categorySelect = new Select<>();
        categorySelect.setLabel("Category");
        categorySelect.setWidth("160px");
        categorySelect.setPlaceholder("All categories");

        scenarioCombo = new ComboBox<>("Scenario");
        scenarioCombo.setItemLabelGenerator(SimulationScenario::displayTitle);
        scenarioCombo.setWidth("240px");
        scenarioCombo.setPlaceholder("Select a scenario...");

        injectionToggle = new Checkbox("Anchor Injection", true);
        injectionToggle.addClassName("ar-inject-toggle");

        tokenBudgetField = new IntegerField("Token Budget (0=off)");
        tokenBudgetField.setMin(0);
        tokenBudgetField.setMax(5000);
        tokenBudgetField.setStepButtonsVisible(true);
        tokenBudgetField.setValue(0);
        tokenBudgetField.setWidth("170px");

        maxTurnsField = new IntegerField("Max Turns");
        maxTurnsField.setMin(1);
        maxTurnsField.setMax(200);
        maxTurnsField.setStepButtonsVisible(true);
        maxTurnsField.setValue(10);
        maxTurnsField.setWidth("120px");

        runButton = new Button("Run");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.setEnabled(false);

        pauseButton = new Button("Pause");
        pauseButton.setVisible(false);

        resumeButton = new Button("Resume");
        resumeButton.setVisible(false);

        stopButton = new Button("Stop");
        stopButton.setVisible(false);

        runHistoryPanel = new RunHistoryPanel(runHistoryStore, simToChatBridge);

        runHistoryButton = new Button("Run History");

        wireButtons();

        var controls = new HorizontalLayout(
                categorySelect, scenarioCombo, tokenBudgetField, maxTurnsField,
                runButton, pauseButton, resumeButton, stopButton, runHistoryButton);
        controls.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        controls.setSpacing(true);
        controls.setWidthFull();

        // --- Intervention banner ---
        interventionBanner = new InterventionImpactBanner();

        // --- Left column: conversation only ---
        conversationPanel = new ConversationPanel();
        conversationPanel.setTurnSelectionCallback(this::onTurnSelected);

        var leftColumn = new VerticalLayout();
        leftColumn.setPadding(false);
        leftColumn.setSpacing(false);
        leftColumn.setSizeFull();
        leftColumn.add(new H4("Conversation"), conversationPanel, injectionToggle);
        leftColumn.setFlexGrow(1, conversationPanel);

        // --- Scenario Details tab content ---
        scenarioDetailsTitle = new Span("Select a scenario");
        scenarioDetailsTitle.addClassName("ar-scenario-brief-title");

        scenarioDetailsObjective = new Paragraph("Select a scenario to view what it tests.");
        scenarioDetailsObjective.addClassName("ar-no-margin");

        scenarioDetailsFocus = new Paragraph("");
        scenarioDetailsFocus.addClassName("ar-scenario-brief-focus");

        scenarioDetailsHighlights = new VerticalLayout();
        scenarioDetailsHighlights.setPadding(false);
        scenarioDetailsHighlights.setSpacing(false);
        scenarioDetailsHighlights.addClassName("ar-brief-highlights");

        scenarioDetailsSetting = new Paragraph("");
        scenarioDetailsSetting.addClassName("ar-scenario-brief-setting");

        scenarioDetailsContent = new VerticalLayout(
                scenarioDetailsTitle,
                scenarioDetailsObjective,
                scenarioDetailsFocus,
                scenarioDetailsHighlights,
                scenarioDetailsSetting);
        scenarioDetailsContent.setPadding(true);
        scenarioDetailsContent.setSpacing(false);

        // --- Right panel: TabSheet ---
        driftSummaryPanel = new DriftSummaryPanel();
        inspectorPanel = new ContextInspectorPanel();
        timelinePanel = new AnchorTimelinePanel();
        manipulationPanel = new AnchorManipulationPanel(anchorRepository, anchorEngine, mutationStrategy);
        knowledgeBrowserPanel = new KnowledgeBrowserPanel(anchorEngine, anchorRepository);

        timelinePanel.setTurnSelectionListener(this::onTurnSelected);

        dispatcher = new ProgressDispatcher();
        dispatcher.addListener(conversationPanel);
        dispatcher.addListener(driftSummaryPanel);
        dispatcher.addListener(inspectorPanel);
        dispatcher.addListener(timelinePanel);
        dispatcher.addListener(knowledgeBrowserPanel);

        rightTabSheet = new TabSheet();
        rightTabSheet.setSizeFull();
        scenarioDetailsTab = rightTabSheet.add("Details", scenarioDetailsContent);
        contextInspectorTab = rightTabSheet.add("Context Inspector", inspectorPanel);
        rightTabSheet.add("Timeline", timelinePanel);
        knowledgeBrowserTab = rightTabSheet.add("Knowledge Browser", knowledgeBrowserPanel);
        resultsTab = rightTabSheet.add("Results", driftSummaryPanel);
        manipulationTab = rightTabSheet.add("Manipulation", manipulationPanel);
        manipulationTab.setVisible(false);
        runHistoryTab = rightTabSheet.add("Run History", runHistoryPanel);

        runHistoryButton.addClickListener(e -> {
            runHistoryPanel.refresh();
            rightTabSheet.setSelectedTab(runHistoryTab);
        });

        inspectorPanel.setBrowseCallback(anchorText -> {
            knowledgeBrowserPanel.filterByAnchorText(anchorText);
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

        add(headerRow, controls, interventionBanner, splitLayout, statusBar);
        setFlexGrow(1, splitLayout);
        updateScenarioContext(null);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        loadScenarios();
        initTheme();
    }

    private void transitionTo(SimControlState target) {
        if (!controlState.canTransitionTo(target)) {
            return;
        }
        controlState = target;

        switch (target) {
            case IDLE -> {
                categorySelect.setEnabled(true);
                scenarioCombo.setEnabled(true);
                tokenBudgetField.setEnabled(true);
                runButton.setEnabled(scenarioCombo.getValue() != null);
                runButton.setVisible(true);
                runHistoryButton.setVisible(true);
                pauseButton.setVisible(false);
                resumeButton.setVisible(false);
                stopButton.setVisible(false);
                progressBar.setVisible(false);
                manipulationTab.setVisible(false);
            }
            case RUNNING -> {
                categorySelect.setEnabled(false);
                scenarioCombo.setEnabled(false);
                tokenBudgetField.setEnabled(false);
                runButton.setVisible(false);
                runHistoryButton.setVisible(false);
                pauseButton.setVisible(true);
                resumeButton.setVisible(false);
                stopButton.setVisible(true);
                manipulationTab.setVisible(false);
                interventionBanner.dismiss();
                rightTabSheet.setSelectedTab(contextInspectorTab);
            }
            case PAUSED -> {
                categorySelect.setEnabled(false);
                scenarioCombo.setEnabled(false);
                runButton.setVisible(false);
                runHistoryButton.setVisible(true);
                pauseButton.setVisible(false);
                resumeButton.setVisible(true);
                stopButton.setVisible(true);
                manipulationTab.setVisible(true);

                var contextId = simulationService.getCurrentContextId();
                if (contextId != null) {
                    anchorCountBeforePause = anchorEngine.activeCount(contextId);
                    manipulationPanel.loadAnchors(contextId);
                }

                rightTabSheet.setSelectedTab(manipulationTab);
            }
            case COMPLETED -> {
                categorySelect.setEnabled(true);
                scenarioCombo.setEnabled(true);
                tokenBudgetField.setEnabled(true);
                runButton.setEnabled(scenarioCombo.getValue() != null);
                runButton.setVisible(true);
                runHistoryButton.setVisible(true);
                pauseButton.setVisible(false);
                resumeButton.setVisible(false);
                stopButton.setVisible(false);
                progressBar.setVisible(false);
                manipulationTab.setVisible(false);
                rightTabSheet.setSelectedTab(resultsTab);
            }
        }
    }

    private void wireButtons() {
        scenarioCombo.addValueChangeListener(e -> {
            runButton.setEnabled(e.getValue() != null && canStartRun());
            updateScenarioContext(e.getValue());
            if (e.getValue() != null) {
                maxTurnsField.setValue(e.getValue().maxTurns());
                if (canStartRun()) {
                    rightTabSheet.setSelectedTab(scenarioDetailsTab);
                }
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

            scenariosByCategory = scenarios.stream()
                                           .collect(Collectors.groupingBy(
                                                   s -> s.category() != null && !s.category().isBlank() ? s.category() : "adversarial",
                                                   TreeMap::new,
                                                   Collectors.collectingAndThen(
                                                           Collectors.toList(),
                                                           list -> list.stream()
                                                                       .sorted((a, b) -> a.displayTitle().compareTo(b.displayTitle()))
                                                                       .toList())));

            categorySelect.setItems(scenariosByCategory.keySet());
            categorySelect.addValueChangeListener(e -> {
                var cat = e.getValue();
                if (cat == null) {
                    return;
                }
                var filtered = scenariosByCategory.getOrDefault(cat, List.of());
                scenarioCombo.setItems(filtered);
                if (!filtered.isEmpty()) {
                    scenarioCombo.setValue(filtered.getFirst());
                }
            });

            var firstCategory = scenariosByCategory.firstKey();
            categorySelect.setValue(firstCategory);
        } catch (Exception e) {
            statusLabel.setText("Failed to load scenarios: " + e.getMessage());
        }
    }

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

        if (knowledgeBrowserPanel.getContextId() == null) {
            knowledgeBrowserPanel.setContextId(simulationService.getCurrentContextId());
        }

        dispatcher.dispatch(progress);

        if (progress.complete()) {
            progressBar.setValue(1.0);
            runHistoryPanel.refresh();
            transitionTo(SimControlState.COMPLETED);
        }
    }

    private void onTurnSelected(int turnNumber) {
        timelinePanel.selectTurn(turnNumber);
    }

    private Integer resolveTokenBudget() {
        var value = tokenBudgetField.getValue();
        return value != null ? Math.max(0, value) : 0;
    }

    private boolean canStartRun() {
        return controlState == SimControlState.IDLE || controlState == SimControlState.COMPLETED;
    }

    private void updateScenarioContext(@Nullable SimulationScenario scenario) {
        scenarioDetailsHighlights.removeAll();
        if (scenario == null) {
            scenarioDetailsTitle.setText("Select a scenario");
            scenarioDetailsObjective.setText("Select a scenario to view what it tests.");
            scenarioDetailsFocus.setText("");
            scenarioDetailsSetting.setText("");
            return;
        }

        scenarioDetailsTitle.setText(scenario.displayTitle());
        scenarioDetailsObjective.setText("Test objective: " + scenario.displayObjective());
        scenarioDetailsFocus.setText("Focus: " + scenario.displayTestFocus());

        for (var highlight : scenario.displayHighlights()) {
            var line = new Span("- " + highlight);
            line.addClassName("ar-scenario-highlight");
            scenarioDetailsHighlights.add(line);
        }

        var settingPreview = normalizeSetting(scenario.setting());
        scenarioDetailsSetting.setText("Setting: " + settingPreview);
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

    private String normalizeSetting(@Nullable String setting) {
        if (setting == null || setting.isBlank()) {
            return "No setting details provided.";
        }
        var normalized = setting.replaceAll("\\s+", " ").trim();
        return normalized;
    }

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
