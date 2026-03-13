package dev.arcmem.simulator.ui.panels;
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
import dev.arcmem.simulator.ui.views.*;

import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Panel for manipulating units during a paused simulation.
 * <p>
 * Visible only when the simulation is in PAUSED state.
 * Provides rank editing, pin toggling, archiving, new unit injection,
 * conflict queue display, and an intervention log.
 */
public class MemoryUnitManipulationPanel extends VerticalLayout {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final MemoryUnitRepository contextUnitRepository;
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitMutationStrategy mutationStrategy;

    private final VerticalLayout unitsListContainer;
    private final VerticalLayout injectFormContainer;
    private final VerticalLayout conflictQueueContainer;
    private final VerticalLayout interventionLogContainer;

    private final List<InterventionEntry> interventionLog = new ArrayList<>();

    private @Nullable String currentContextId;

    /**
     * Represents a single intervention action recorded during a pause session.
     */
    public record InterventionEntry(
            Instant timestamp,
            ActionType actionType,
            String targetUnitId,
            String beforeValue,
            String afterValue
    ) {}

    public enum ActionType {
        RANK_CHANGE,
        ARCHIVE,
        DISABLE,
        ENABLE,
        PIN_TOGGLE,
        INJECT,
        REVISE,
        CONFLICT_RESOLVE
    }

    public MemoryUnitManipulationPanel(MemoryUnitRepository contextUnitRepository, ArcMemEngine arcMemEngine,
                                   MemoryUnitMutationStrategy mutationStrategy) {
        this.contextUnitRepository = contextUnitRepository;
        this.arcMemEngine = arcMemEngine;
        this.mutationStrategy = mutationStrategy;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("ar-manipulation-scroll");

        unitsListContainer = new VerticalLayout();
        unitsListContainer.setPadding(false);
        unitsListContainer.setSpacing(true);

        injectFormContainer = new VerticalLayout();
        injectFormContainer.setPadding(false);
        injectFormContainer.setSpacing(true);
        buildInjectForm();

        conflictQueueContainer = new VerticalLayout();
        conflictQueueContainer.setPadding(false);
        conflictQueueContainer.setSpacing(true);

        interventionLogContainer = new VerticalLayout();
        interventionLogContainer.setPadding(false);
        interventionLogContainer.setSpacing(true);

        add(
                new H4("Memory Units"),
                unitsListContainer,
                new H4("Inject New Memory Unit"),
                injectFormContainer,
                new H4("Conflict Queue"),
                conflictQueueContainer,
                new H4("Intervention Log"),
                interventionLogContainer
        );

        showPlaceholder(unitsListContainer, "Pause the simulation to manipulate units.");
        showPlaceholder(conflictQueueContainer, "No conflicts detected.");
        showPlaceholder(interventionLogContainer, "No interventions recorded.");
    }

    /**
     * Load active units for the given context and render editing controls.
     */
    public void loadUnits(String contextId) {
        this.currentContextId = contextId;
        unitsListContainer.removeAll();

        var nodes = contextUnitRepository.findUnitsByContext(contextId);
        if (nodes.isEmpty()) {
            showPlaceholder(unitsListContainer, "No units for this context.");
            return;
        }

        for (var node : nodes) {
            unitsListContainer.add(buildUnitEditCard(node));
        }

        refreshConflictQueue(contextId);
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        currentContextId = null;
        unitsListContainer.removeAll();
        conflictQueueContainer.removeAll();
        interventionLogContainer.removeAll();
        interventionLog.clear();
        showPlaceholder(unitsListContainer, "Pause the simulation to manipulate units.");
        showPlaceholder(conflictQueueContainer, "No conflicts detected.");
        showPlaceholder(interventionLogContainer, "No interventions recorded.");
    }

    /**
     * Return the list of interventions recorded during this pause session.
     */
    public List<InterventionEntry> getInterventionLog() {
        return List.copyOf(interventionLog);
    }

    /**
     * Return the count of interventions recorded during this pause session.
     */
    public int getInterventionCount() {
        return interventionLog.size();
    }

    private Div buildUnitEditCard(PropositionNode node) {
        var card = new Div();
        card.addClassName("ar-card");
        card.addClassName("ar-card--padded");

        var textSpan = new Span(node.getText());
        textSpan.addClassName("ar-text-block");
        card.add(textSpan);

        var authorityValue = node.getAuthority() != null ? node.getAuthority() : "PROVISIONAL";
        var authorityBadge = new Span(authorityValue);
        authorityBadge.addClassName("ar-badge");
        authorityBadge.getElement().setAttribute("data-authority", authorityValue.toLowerCase());

        var status = node.getStatus() != null ? node.getStatus().name() : "UNKNOWN";
        var statusBadge = new Span(status);
        statusBadge.addClassName("ar-status-badge");
        statusBadge.getElement().setAttribute("data-status",
                                              status.equals("ACTIVE") ? "active" : "other");

        var rankField = new IntegerField("Rank");
        rankField.setMin(MemoryUnit.MIN_RANK);
        rankField.setMax(MemoryUnit.MAX_RANK);
        rankField.setValue(node.getRank() > 0 ? node.getRank() : 500);
        rankField.setStepButtonsVisible(true);
        rankField.setStep(50);
        rankField.setWidth("160px");

        var applyRank = new Button("Apply");
        applyRank.addThemeVariants(ButtonVariant.LUMO_SMALL);
        applyRank.addClickListener(e -> {
            var newRank = rankField.getValue();
            if (newRank != null) {
                int oldRank = node.getRank();
                int clamped = MemoryUnit.clampRank(newRank);
                contextUnitRepository.updateRank(node.getId(), clamped);
                recordIntervention(ActionType.RANK_CHANGE, node.getId(),
                                   String.valueOf(oldRank), String.valueOf(clamped));
                if (currentContextId != null) {
                    loadUnits(currentContextId);
                }
            }
        });

        var rankRow = new HorizontalLayout(rankField, applyRank);
        rankRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        var pinToggle = new Checkbox("Pinned", node.isPinned());
        pinToggle.addValueChangeListener(e -> {
            contextUnitRepository.updatePinned(node.getId(), e.getValue());
            recordIntervention(ActionType.PIN_TOGGLE, node.getId(),
                               String.valueOf(!e.getValue()), String.valueOf(e.getValue()));
        });

        var isActive = node.getStatus() == PropositionStatus.ACTIVE;
        var toggleButton = new Button(isActive ? "Disable" : "Enable");
        toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL, isActive
                ? ButtonVariant.LUMO_ERROR
                : ButtonVariant.LUMO_SUCCESS);
        toggleButton.addClickListener(e -> {
            if (isActive) {
                if (node.isPinned()) {
                    var dialog = new ConfirmDialog(
                            "Disable Pinned MemoryUnit",
                            "This unit is pinned. Are you sure you want to disable it?",
                            "Disable",
                            confirmEvent -> disableUnit(node)
                    );
                    dialog.setCancelable(true);
                    dialog.open();
                } else {
                    disableUnit(node);
                }
            } else {
                var desiredRank = rankField.getValue() != null ? rankField.getValue() : 500;
                enableUnit(node, desiredRank);
            }
        });

        var controlsRow = new HorizontalLayout(authorityBadge, statusBadge, pinToggle, toggleButton);
        controlsRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        controlsRow.setSpacing(true);

        var reviseField = new com.vaadin.flow.component.textfield.TextField("Revision");
        reviseField.setWidthFull();
        reviseField.setValue(node.getText());

        var reviseButton = new Button("Revise");
        reviseButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        reviseButton.addClickListener(e -> {
            var text = reviseField.getValue();
            if (text == null || text.isBlank() || text.equals(node.getText())) {
                return;
            }
            var request = new MutationRequest(node.getId(), text, MutationSource.UI, "sim-operator");
            if (mutationStrategy.evaluate(request) instanceof MutationDecision.Allow) {
                executeRevision(node, text);
                recordIntervention(ActionType.REVISE, node.getId(), node.getText(), text);
                if (currentContextId != null) {
                    loadUnits(currentContextId);
                }
            }
        });

        var reviseRow = new HorizontalLayout(reviseField, reviseButton);
        reviseRow.setWidthFull();
        reviseRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        reviseRow.expand(reviseField);

        card.add(controlsRow, rankRow, reviseRow);
        return card;
    }

    private void disableUnit(PropositionNode node) {
        contextUnitRepository.deactivateUnit(node.getId());
        recordIntervention(ActionType.DISABLE, node.getId(),
                           "ACTIVE (rank=" + node.getRank() + ")", "SUPERSEDED");
        if (currentContextId != null) {
            loadUnits(currentContextId);
        }
    }

    private void enableUnit(PropositionNode node, int rank) {
        contextUnitRepository.activateUnit(node.getId(), MemoryUnit.clampRank(rank));
        recordIntervention(ActionType.ENABLE, node.getId(),
                           "SUPERSEDED", "ACTIVE (rank=" + MemoryUnit.clampRank(rank) + ")");
        if (currentContextId != null) {
            loadUnits(currentContextId);
        }
    }

    private void executeRevision(PropositionNode predecessor, String revisedText) {
        var successor = new PropositionNode(UUID.randomUUID().toString(), "default", revisedText, 0.95, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        successor.setContextId(currentContextId);
        contextUnitRepository.saveNode(successor);
        contextUnitRepository.promoteToUnit(successor.getId(), predecessor.getRank(), predecessor.getAuthority());
        if (predecessor.isPinned()) {
            contextUnitRepository.updatePinned(successor.getId(), true);
        }
        arcMemEngine.supersede(predecessor.getId(), successor.getId(), ArchiveReason.REVISION);
    }

    private void buildInjectForm() {
        var textArea = new TextArea("Unit Text");
        textArea.setWidthFull();
        textArea.setMinHeight("60px");
        textArea.setRequired(true);
        textArea.setPlaceholder("Enter the proposition text for the new unit...");

        var rankField = new IntegerField("Initial Rank");
        rankField.setMin(MemoryUnit.MIN_RANK);
        rankField.setMax(MemoryUnit.MAX_RANK);
        rankField.setValue(500);
        rankField.setStepButtonsVisible(true);
        rankField.setStep(50);
        rankField.setWidth("160px");

        var authorityCombo = new ComboBox<String>("Authority");
        authorityCombo.setItems("PROVISIONAL", "UNRELIABLE", "RELIABLE");
        authorityCombo.setValue("PROVISIONAL");
        authorityCombo.setWidth("160px");

        var submitButton = new Button("Inject MemoryUnit");
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.addClickListener(e -> {
            var text = textArea.getValue();
            if (text == null || text.isBlank()) {
                textArea.setInvalid(true);
                textArea.setErrorMessage("Unit text is required.");
                return;
            }
            if (currentContextId == null) {
                return;
            }

            var rank = rankField.getValue() != null ? rankField.getValue() : 500;
            var authority = authorityCombo.getValue() != null ? authorityCombo.getValue() : "PROVISIONAL";

            var node = new PropositionNode(
                    UUID.randomUUID().toString(),
                    currentContextId,
                    text,
                    1.0,
                    0.0,
                    "Manual injection via manipulation panel",
                    List.of(),
                    Instant.now(),
                    Instant.now(),
                    PropositionStatus.ACTIVE,
                    null,
                    List.of(),
                    0,
                    null,
                    false,
                    null,
                    null,
                    0,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            contextUnitRepository.saveNode(node);
            contextUnitRepository.promoteToUnit(node.getId(), MemoryUnit.clampRank(rank), authority);

            recordIntervention(ActionType.INJECT, node.getId(),
                               "N/A", "rank=" + rank + " authority=" + authority);

            textArea.clear();
            rankField.setValue(500);
            authorityCombo.setValue("PROVISIONAL");

            loadUnits(currentContextId);
        });

        var formRow = new HorizontalLayout(rankField, authorityCombo, submitButton);
        formRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        injectFormContainer.add(textArea, formRow);
    }

    private void refreshConflictQueue(String contextId) {
        conflictQueueContainer.removeAll();

        var units = arcMemEngine.inject(contextId);
        var seen = new HashSet<String>();
        var allConflicts = new ArrayList<ConflictDetector.Conflict>();

        for (var unit : units) {
            var conflicts = arcMemEngine.detectConflicts(contextId, unit.text());
            for (var conflict : conflicts) {
                // Deduplicate: use sorted pair of IDs as key
                var pairKey = conflict.existing().id().compareTo(unit.id()) < 0
                        ? conflict.existing().id() + ":" + unit.id()
                        : unit.id() + ":" + conflict.existing().id();
                if (seen.add(pairKey)) {
                    allConflicts.add(conflict);
                }
            }
        }

        if (allConflicts.isEmpty()) {
            showPlaceholder(conflictQueueContainer, "No conflicts detected.");
            return;
        }

        for (var conflict : allConflicts) {
            conflictQueueContainer.add(buildConflictCard(conflict));
        }
    }

    private Div buildConflictCard(ConflictDetector.Conflict conflict) {
        var card = new Div();
        card.addClassName("ar-card--conflict");

        var existingLabel = new Span("Existing: " + conflict.existing().text());
        existingLabel.addClassName("ar-text-block--tight");

        var incomingLabel = new Span("Incoming: " + conflict.incomingText());
        incomingLabel.addClassName("ar-text-block--tight");

        var resolution = arcMemEngine.resolveConflict(conflict);
        var recommendationLabel = new Span("Recommendation: " + resolution.name());
        recommendationLabel.addClassName("ar-recommendation");

        var acceptButton = new Button("Accept");
        acceptButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        acceptButton.addClickListener(e -> {
            if (resolution == ConflictResolver.Resolution.REPLACE) {
                arcMemEngine.archive(conflict.existing().id(), ArchiveReason.CONFLICT_REPLACEMENT);
            }
            recordIntervention(ActionType.CONFLICT_RESOLVE, conflict.existing().id(),
                               "conflict", "accepted: " + resolution.name());
            if (currentContextId != null) {
                loadUnits(currentContextId);
            }
        });

        var dismissButton = new Button("Dismiss");
        dismissButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        dismissButton.addClickListener(e -> {
            recordIntervention(ActionType.CONFLICT_RESOLVE, conflict.existing().id(),
                               "conflict", "dismissed");
            if (currentContextId != null) {
                loadUnits(currentContextId);
            }
        });

        var actions = new HorizontalLayout(acceptButton, dismissButton);
        actions.setSpacing(true);

        card.add(existingLabel, incomingLabel, recommendationLabel, actions);
        return card;
    }

    private void recordIntervention(ActionType actionType, String targetId,
                                    String beforeValue, String afterValue) {
        var entry = new InterventionEntry(
                Instant.now(), actionType, targetId, beforeValue, afterValue);
        interventionLog.add(entry);
        renderInterventionLog();
    }

    private void renderInterventionLog() {
        interventionLogContainer.removeAll();

        if (interventionLog.isEmpty()) {
            showPlaceholder(interventionLogContainer, "No interventions recorded.");
            return;
        }

        for (int i = interventionLog.size() - 1; i >= 0; i--) {
            var entry = interventionLog.get(i);
            var row = new Div();
            row.addClassName("ar-log-row");

            var time = LocalDateTime.ofInstant(entry.timestamp(), ZoneId.systemDefault())
                                    .format(TIME_FORMAT);

            var text = new Span("[%s] %s | %s | %s -> %s".formatted(
                    time,
                    entry.actionType().name(),
                    truncateId(entry.targetUnitId()),
                    entry.beforeValue(),
                    entry.afterValue()));
            text.addClassName("ar-log-monospace");

            row.add(text);
            interventionLogContainer.add(row);
        }
    }

    private String truncateId(String id) {
        return id.length() > 8 ? id.substring(0, 8) + "..." : id;
    }

    private void showPlaceholder(VerticalLayout container, String message) {
        var placeholder = new Paragraph(message);
        placeholder.addClassName("ar-placeholder");
        container.add(placeholder);
    }
}
