package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import dev.dunnam.diceanchors.sim.benchmark.AblationCondition;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentDefinition;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Configuration panel for setting up ablation experiments.
 * <p>
 * Presents checkboxes for ablation conditions, a multi-select for scenarios,
 * and configuration fields for repetition count, evaluator model, and experiment name.
 * The Run button is enabled only when at least 2 conditions and 1 scenario are selected.
 * <p>
 * Use {@link #setOnRunExperiment(Consumer)} to receive the constructed
 * {@link ExperimentDefinition} when the user initiates a run.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>EC1</strong>: Run button is disabled unless at least 2 conditions and 1 scenario are selected.</li>
 *   <li><strong>EC2</strong>: Experiment name is auto-generated as "Experiment YYYY-MM-DD" when blank.</li>
 *   <li><strong>EC3</strong>: Repetition count is always in [2, 20].</li>
 * </ul>
 */
public class ExperimentConfigPanel extends VerticalLayout {

    private static final Map<String, AblationCondition> CONDITION_BY_NAME = Map.of(
            "FULL_ANCHORS", AblationCondition.FULL_ANCHORS,
            "NO_ANCHORS", AblationCondition.NO_ANCHORS,
            "FLAT_AUTHORITY", AblationCondition.FLAT_AUTHORITY,
            "NO_RANK_DIFFERENTIATION", AblationCondition.NO_RANK_DIFFERENTIATION
    );

    private final List<SimulationScenario> scenarios;

    private final CheckboxGroup<String> conditionsGroup;
    private final MultiSelectComboBox<String> scenariosSelect;
    private final IntegerField repetitionsField;
    private final TextField evaluatorModelField;
    private final TextField experimentNameField;
    private final Button runButton;

    private Consumer<ExperimentDefinition> onRunExperiment;

    public ExperimentConfigPanel(List<SimulationScenario> scenarios) {
        this.scenarios = scenarios != null ? List.copyOf(scenarios) : List.of();

        setPadding(true);
        setSpacing(true);
        setWidthFull();

        var conditionsTitle = new H4("Ablation Conditions");
        conditionsTitle.addClassName("ar-section-title");

        conditionsGroup = new CheckboxGroup<>();
        conditionsGroup.setItems("FULL_ANCHORS", "NO_ANCHORS", "FLAT_AUTHORITY", "NO_RANK_DIFFERENTIATION");
        conditionsGroup.addClassName("ar-experiment-conditions");
        conditionsGroup.addValueChangeListener(e -> updateRunButtonState());

        // --- Scenarios ---
        var scenariosTitle = new H4("Scenarios");
        scenariosTitle.addClassName("ar-section-title");

        scenariosSelect = new MultiSelectComboBox<>("Select Scenarios");
        scenariosSelect.setWidthFull();
        scenariosSelect.setItems(buildScenarioItems());
        scenariosSelect.setItemLabelGenerator(id -> labelForScenario(id));
        scenariosSelect.addValueChangeListener(e -> updateRunButtonState());

        // --- Configuration ---
        var configTitle = new H4("Configuration");
        configTitle.addClassName("ar-section-title");

        repetitionsField = new IntegerField("Repetitions per Cell");
        repetitionsField.setMin(2);
        repetitionsField.setMax(20);
        repetitionsField.setValue(5);
        repetitionsField.setStepButtonsVisible(true);
        repetitionsField.setWidth("200px");

        evaluatorModelField = new TextField("Evaluator Model Override");
        evaluatorModelField.setPlaceholder("default: scenario model");
        evaluatorModelField.setWidthFull();

        experimentNameField = new TextField("Experiment Name");
        experimentNameField.setPlaceholder("auto-generated if blank");
        experimentNameField.setWidthFull();

        // --- Run Button ---
        runButton = new Button("Run Experiment");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.setEnabled(false);
        runButton.addClickListener(e -> fireRunExperiment());

        add(
                conditionsTitle,
                conditionsGroup,
                scenariosTitle,
                scenariosSelect,
                configTitle,
                repetitionsField,
                evaluatorModelField,
                experimentNameField,
                runButton
        );
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Set the callback invoked when the user clicks "Run Experiment".
     * The callback receives the fully constructed {@link ExperimentDefinition}.
     */
    public void setOnRunExperiment(Consumer<ExperimentDefinition> callback) {
        this.onRunExperiment = callback;
    }

    /**
     * Reset all fields to their defaults.
     */
    public void reset() {
        conditionsGroup.deselectAll();
        scenariosSelect.deselectAll();
        repetitionsField.setValue(5);
        evaluatorModelField.clear();
        experimentNameField.clear();
        runButton.setEnabled(false);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> buildScenarioItems() {
        return this.scenarios.stream()
                             .map(SimulationScenario::id)
                             .toList();
    }

    private String labelForScenario(String id) {
        return this.scenarios.stream()
                             .filter(s -> id.equals(s.id()))
                             .findFirst()
                             .map(s -> {
                                 var category = s.category();
                                 if (category != null && !category.isBlank()) {
                                     return "[%s] %s".formatted(category, id);
                                 }
                                 return id;
                             })
                             .orElse(id);
    }

    private void updateRunButtonState() {
        var conditionCount = conditionsGroup.getSelectedItems().size();
        var scenarioCount = scenariosSelect.getSelectedItems().size();
        runButton.setEnabled(conditionCount >= 2 && scenarioCount >= 1);
    }

    private void fireRunExperiment() {
        if (onRunExperiment == null) {
            return;
        }

        var selectedConditionNames = conditionsGroup.getSelectedItems();
        var conditions = selectedConditionNames.stream()
                                               .map(CONDITION_BY_NAME::get)
                                               .filter(c -> c != null)
                                               .toList();

        var selectedScenarioIds = List.copyOf(scenariosSelect.getSelectedItems());

        var reps = repetitionsField.getValue() != null ? repetitionsField.getValue() : 5;

        var modelText = evaluatorModelField.getValue();
        var evaluatorModel = (modelText != null && !modelText.isBlank())
                ? Optional.of(modelText.trim())
                : Optional.<String> empty();

        var nameText = experimentNameField.getValue();
        var experimentName = (nameText != null && !nameText.isBlank())
                ? nameText.trim()
                : "Experiment %s".formatted(LocalDate.now());

        var definition = new ExperimentDefinition(
                experimentName,
                conditions,
                selectedScenarioIds,
                reps,
                evaluatorModel
        );

        onRunExperiment.accept(definition);
    }
}
