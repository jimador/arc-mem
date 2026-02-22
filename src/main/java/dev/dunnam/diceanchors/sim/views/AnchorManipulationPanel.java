package dev.dunnam.diceanchors.sim.views;

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
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.ConflictDetector;
import dev.dunnam.diceanchors.anchor.ConflictResolver;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
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
 * Panel for manipulating anchors during a paused simulation.
 * <p>
 * Visible only when the simulation is in PAUSED state.
 * Provides rank editing, pin toggling, archiving, new anchor injection,
 * conflict queue display, and an intervention log.
 */
public class AnchorManipulationPanel extends VerticalLayout {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final AnchorRepository anchorRepository;
    private final AnchorEngine anchorEngine;

    private final VerticalLayout anchorsListContainer;
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
            String targetAnchorId,
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
        CONFLICT_RESOLVE
    }

    public AnchorManipulationPanel(AnchorRepository anchorRepository, AnchorEngine anchorEngine) {
        this.anchorRepository = anchorRepository;
        this.anchorEngine = anchorEngine;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("ar-manipulation-scroll");

        // --- Active anchors section ---
        anchorsListContainer = new VerticalLayout();
        anchorsListContainer.setPadding(false);
        anchorsListContainer.setSpacing(true);

        // --- Inject form section ---
        injectFormContainer = new VerticalLayout();
        injectFormContainer.setPadding(false);
        injectFormContainer.setSpacing(true);
        buildInjectForm();

        // --- Conflict queue section ---
        conflictQueueContainer = new VerticalLayout();
        conflictQueueContainer.setPadding(false);
        conflictQueueContainer.setSpacing(true);

        // --- Intervention log section ---
        interventionLogContainer = new VerticalLayout();
        interventionLogContainer.setPadding(false);
        interventionLogContainer.setSpacing(true);

        add(
                new H4("Anchors"),
                anchorsListContainer,
                new H4("Inject New Anchor"),
                injectFormContainer,
                new H4("Conflict Queue"),
                conflictQueueContainer,
                new H4("Intervention Log"),
                interventionLogContainer
        );

        showPlaceholder(anchorsListContainer, "Pause the simulation to manipulate anchors.");
        showPlaceholder(conflictQueueContainer, "No conflicts detected.");
        showPlaceholder(interventionLogContainer, "No interventions recorded.");
    }

    /**
     * Load active anchors for the given context and render editing controls.
     */
    public void loadAnchors(String contextId) {
        this.currentContextId = contextId;
        anchorsListContainer.removeAll();

        var nodes = anchorRepository.findAnchorsByContext(contextId);
        if (nodes.isEmpty()) {
            showPlaceholder(anchorsListContainer, "No anchors for this context.");
            return;
        }

        for (var node : nodes) {
            anchorsListContainer.add(buildAnchorEditCard(node));
        }

        refreshConflictQueue(contextId);
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        currentContextId = null;
        anchorsListContainer.removeAll();
        conflictQueueContainer.removeAll();
        interventionLogContainer.removeAll();
        interventionLog.clear();
        showPlaceholder(anchorsListContainer, "Pause the simulation to manipulate anchors.");
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

    // -------------------------------------------------------------------------
    // Anchor edit card (10.1)
    // -------------------------------------------------------------------------

    private Div buildAnchorEditCard(PropositionNode node) {
        var card = new Div();
        card.addClassName("ar-card");
        card.addClassName("ar-card--padded");

        // Text
        var textSpan = new Span(node.getText());
        textSpan.addClassName("ar-text-block");
        card.add(textSpan);

        // Authority + status badges
        var authorityValue = node.getAuthority() != null ? node.getAuthority() : "PROVISIONAL";
        var authorityBadge = new Span(authorityValue);
        authorityBadge.addClassName("ar-badge");
        authorityBadge.getElement().setAttribute("data-authority", authorityValue.toLowerCase());

        var status = node.getStatus() != null ? node.getStatus().name() : "UNKNOWN";
        var statusBadge = new Span(status);
        statusBadge.addClassName("ar-status-badge");
        statusBadge.getElement().setAttribute("data-status",
                status.equals("ACTIVE") ? "active" : "other");

        // Rank slider
        var rankField = new IntegerField("Rank");
        rankField.setMin(Anchor.MIN_RANK);
        rankField.setMax(Anchor.MAX_RANK);
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
                int clamped = Anchor.clampRank(newRank);
                anchorRepository.updateRank(node.getId(), clamped);
                recordIntervention(ActionType.RANK_CHANGE, node.getId(),
                                   String.valueOf(oldRank), String.valueOf(clamped));
                if (currentContextId != null) {
                    loadAnchors(currentContextId);
                }
            }
        });

        var rankRow = new HorizontalLayout(rankField, applyRank);
        rankRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        // Pin toggle
        var pinToggle = new Checkbox("Pinned", node.isPinned());
        pinToggle.addValueChangeListener(e -> {
            anchorRepository.updatePinned(node.getId(), e.getValue());
            recordIntervention(ActionType.PIN_TOGGLE, node.getId(),
                               String.valueOf(!e.getValue()), String.valueOf(e.getValue()));
        });

        // Enable/Disable button
        var isActive = node.getStatus() == PropositionStatus.ACTIVE;
        var toggleButton = new Button(isActive ? "Disable" : "Enable");
        toggleButton.addThemeVariants(ButtonVariant.LUMO_SMALL, isActive
                ? ButtonVariant.LUMO_ERROR
                : ButtonVariant.LUMO_SUCCESS);
        toggleButton.addClickListener(e -> {
            if (isActive) {
                if (node.isPinned()) {
                    var dialog = new ConfirmDialog(
                            "Disable Pinned Anchor",
                            "This anchor is pinned. Are you sure you want to disable it?",
                            "Disable",
                            confirmEvent -> disableAnchor(node)
                    );
                    dialog.setCancelable(true);
                    dialog.open();
                } else {
                    disableAnchor(node);
                }
            } else {
                var desiredRank = rankField.getValue() != null ? rankField.getValue() : 500;
                enableAnchor(node, desiredRank);
            }
        });

        var controlsRow = new HorizontalLayout(authorityBadge, statusBadge, pinToggle, toggleButton);
        controlsRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        controlsRow.setSpacing(true);

        card.add(controlsRow, rankRow);
        return card;
    }

    private void disableAnchor(PropositionNode node) {
        anchorRepository.deactivateAnchor(node.getId());
        recordIntervention(ActionType.DISABLE, node.getId(),
                           "ACTIVE (rank=" + node.getRank() + ")", "SUPERSEDED");
        if (currentContextId != null) {
            loadAnchors(currentContextId);
        }
    }

    private void enableAnchor(PropositionNode node, int rank) {
        anchorRepository.activateAnchor(node.getId(), Anchor.clampRank(rank));
        recordIntervention(ActionType.ENABLE, node.getId(),
                           "SUPERSEDED", "ACTIVE (rank=" + Anchor.clampRank(rank) + ")");
        if (currentContextId != null) {
            loadAnchors(currentContextId);
        }
    }

    // -------------------------------------------------------------------------
    // Inject form (10.2)
    // -------------------------------------------------------------------------

    private void buildInjectForm() {
        var textArea = new TextArea("Anchor Text");
        textArea.setWidthFull();
        textArea.setMinHeight("60px");
        textArea.setRequired(true);
        textArea.setPlaceholder("Enter the proposition text for the new anchor...");

        var rankField = new IntegerField("Initial Rank");
        rankField.setMin(Anchor.MIN_RANK);
        rankField.setMax(Anchor.MAX_RANK);
        rankField.setValue(500);
        rankField.setStepButtonsVisible(true);
        rankField.setStep(50);
        rankField.setWidth("160px");

        var authorityCombo = new ComboBox<String>("Authority");
        authorityCombo.setItems("PROVISIONAL", "UNRELIABLE", "RELIABLE");
        authorityCombo.setValue("PROVISIONAL");
        authorityCombo.setWidth("160px");

        var submitButton = new Button("Inject Anchor");
        submitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submitButton.addClickListener(e -> {
            var text = textArea.getValue();
            if (text == null || text.isBlank()) {
                textArea.setInvalid(true);
                textArea.setErrorMessage("Anchor text is required.");
                return;
            }
            if (currentContextId == null) {
                return;
            }

            var rank = rankField.getValue() != null ? rankField.getValue() : 500;
            var authority = authorityCombo.getValue() != null ? authorityCombo.getValue() : "PROVISIONAL";

            // Create a PropositionNode and save it, then promote
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
                    null
            );

            // Save then promote
            anchorRepository.saveNode(node);
            anchorRepository.promoteToAnchor(node.getId(), Anchor.clampRank(rank), authority);

            recordIntervention(ActionType.INJECT, node.getId(),
                               "N/A", "rank=" + rank + " authority=" + authority);

            textArea.clear();
            rankField.setValue(500);
            authorityCombo.setValue("PROVISIONAL");

            loadAnchors(currentContextId);
        });

        var formRow = new HorizontalLayout(rankField, authorityCombo, submitButton);
        formRow.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        injectFormContainer.add(textArea, formRow);
    }

    // -------------------------------------------------------------------------
    // Conflict queue (10.3)
    // -------------------------------------------------------------------------

    private void refreshConflictQueue(String contextId) {
        conflictQueueContainer.removeAll();

        var anchors = anchorEngine.inject(contextId);
        var seen = new HashSet<String>();
        var allConflicts = new ArrayList<ConflictDetector.Conflict>();

        for (var anchor : anchors) {
            var conflicts = anchorEngine.detectConflicts(contextId, anchor.text());
            for (var conflict : conflicts) {
                // Deduplicate: use sorted pair of IDs as key
                var pairKey = conflict.existing().id().compareTo(anchor.id()) < 0
                        ? conflict.existing().id() + ":" + anchor.id()
                        : anchor.id() + ":" + conflict.existing().id();
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

        var resolution = anchorEngine.resolveConflict(conflict);
        var recommendationLabel = new Span("Recommendation: " + resolution.name());
        recommendationLabel.addClassName("ar-recommendation");

        var acceptButton = new Button("Accept");
        acceptButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        acceptButton.addClickListener(e -> {
            if (resolution == ConflictResolver.Resolution.REPLACE) {
                anchorRepository.archiveAnchor(conflict.existing().id());
            }
            recordIntervention(ActionType.CONFLICT_RESOLVE, conflict.existing().id(),
                               "conflict", "accepted: " + resolution.name());
            if (currentContextId != null) {
                loadAnchors(currentContextId);
            }
        });

        var dismissButton = new Button("Dismiss");
        dismissButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        dismissButton.addClickListener(e -> {
            recordIntervention(ActionType.CONFLICT_RESOLVE, conflict.existing().id(),
                               "conflict", "dismissed");
            if (currentContextId != null) {
                loadAnchors(currentContextId);
            }
        });

        var actions = new HorizontalLayout(acceptButton, dismissButton);
        actions.setSpacing(true);

        card.add(existingLabel, incomingLabel, recommendationLabel, actions);
        return card;
    }

    // -------------------------------------------------------------------------
    // Intervention log (10.4)
    // -------------------------------------------------------------------------

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
                    truncateId(entry.targetAnchorId()),
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
