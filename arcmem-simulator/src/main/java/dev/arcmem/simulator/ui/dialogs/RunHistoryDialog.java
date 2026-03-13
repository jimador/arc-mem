package dev.arcmem.simulator.ui.dialogs;
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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.arcmem.simulator.chat.SimToChatBridge;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.history.SimulationRunRecord;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Dialog listing completed simulation runs from the {@link RunHistoryStore}.
 * <p>
 * Supports single-run inspection (navigates to {@code /run?runId=X}),
 * deletion with confirmation, and two-run comparison via checkbox selection
 * (navigates to {@code /run?runId=X&compare=Y}).
 */
public class RunHistoryDialog extends Dialog {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final RunHistoryStore runStore;
    private final SimToChatBridge simToChatBridge;
    private final Grid<SimulationRunRecord> grid;
    private final Set<String> selectedRunIds = new HashSet<>();
    private final Button compareButton;

    public RunHistoryDialog(RunHistoryStore runStore, SimToChatBridge simToChatBridge) {
        this.runStore = runStore;
        this.simToChatBridge = simToChatBridge;

        setHeaderTitle("Run History");
        setWidth("900px");
        setHeight("600px");
        setDraggable(true);
        setResizable(true);

        grid = new Grid<>();
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.MULTI);

        grid.addColumn(SimulationRunRecord::scenarioId)
            .setHeader("Scenario")
            .setFlexGrow(2)
            .setSortable(true);

        grid.addColumn(record -> DATE_FORMAT.format(record.startedAt()))
            .setHeader("Date")
            .setWidth("160px")
            .setSortable(true);

        grid.addColumn(record -> String.valueOf(record.turnSnapshots().size()))
            .setHeader("Turns")
            .setWidth("70px");

        grid.addColumn(record -> "%.0f%%".formatted(record.resilienceRate() * 100))
            .setHeader("Resilience")
            .setWidth("100px")
            .setSortable(true);

        grid.addColumn(record -> String.valueOf(record.interventionCount()))
            .setHeader("Interventions")
            .setWidth("110px");

        grid.addColumn(record -> record.injectionEnabled() ? "ON" : "OFF")
            .setHeader("Injection")
            .setWidth("90px");

        grid.addComponentColumn(this::createActions)
            .setHeader("Actions")
            .setWidth("260px");

        compareButton = new Button("Compare Selected");

        grid.asMultiSelect().addValueChangeListener(event -> {
            selectedRunIds.clear();
            event.getValue().forEach(r -> selectedRunIds.add(r.runId()));
            compareButton.setEnabled(selectedRunIds.size() == 2);
        });
        compareButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        compareButton.setEnabled(false);
        compareButton.addClickListener(e -> onCompare());

        var footer = new HorizontalLayout(compareButton);
        footer.setSpacing(true);

        var content = new VerticalLayout(grid, footer);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);
        content.setFlexGrow(1, grid);

        add(content);
        refreshGrid();
    }

    /**
     * Reload the grid data from the store.
     */
    public void refreshGrid() {
        var runs = runStore.list();
        grid.setItems(runs);
        selectedRunIds.clear();
        compareButton.setEnabled(false);
    }

    private HorizontalLayout createActions(SimulationRunRecord record) {
        var inspectButton = new Button("Inspect");
        inspectButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        inspectButton.addClickListener(e -> {
            close();
            UI.getCurrent().navigate("run?runId=" + record.runId());
        });

        var chatButton = new Button("Start Chat");
        chatButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);
        chatButton.addClickListener(e -> {
            var conversationId = simToChatBridge.cloneRunToConversation(record);
            close();
            UI.getCurrent().navigate("chat?conversationId=" + conversationId);
        });

        var deleteButton = new Button("Delete");
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteButton.addClickListener(e -> confirmDelete(record));

        var layout = new HorizontalLayout(inspectButton, chatButton, deleteButton);
        layout.setSpacing(true);
        layout.setPadding(false);
        return layout;
    }

    private void confirmDelete(SimulationRunRecord record) {
        var dialog = new ConfirmDialog();
        dialog.setHeader("Delete Run");
        dialog.setText("Delete run '%s' from %s?".formatted(
                record.scenarioId(), DATE_FORMAT.format(record.startedAt())));
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            runStore.delete(record.runId());
            refreshGrid();
            Notification.show("Run deleted.", 3000, Notification.Position.BOTTOM_START);
        });
        dialog.open();
    }

    private void onCompare() {
        if (selectedRunIds.size() != 2) {
            return;
        }
        var ids = selectedRunIds.iterator();
        var first = ids.next();
        var second = ids.next();
        close();
        UI.getCurrent().navigate("run?runId=" + first + "&compare=" + second);
    }
}
