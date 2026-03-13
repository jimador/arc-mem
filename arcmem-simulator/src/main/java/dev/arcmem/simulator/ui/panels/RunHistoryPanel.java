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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import dev.arcmem.simulator.chat.SimToChatBridge;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.history.SimulationRunRecord;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunHistoryPanel extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RunHistoryStore runStore;
    private final SimToChatBridge simToChatBridge;
    private final Grid<SimulationRunRecord> grid;
    private final ComboBox<String> scenarioFilter;
    private final Select<String> injectionFilter;
    private final Button compareButton;
    private final Set<String> selectedRunIds = new HashSet<>();
    private List<SimulationRunRecord> allRuns = List.of();

    public RunHistoryPanel(RunHistoryStore runStore, SimToChatBridge simToChatBridge) {
        this.runStore = runStore;
        this.simToChatBridge = simToChatBridge;
        setPadding(false);
        setSpacing(true);
        setSizeFull();

        var header = new H4("Run History");
        header.addClassName("ar-panel-title");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        refreshButton.addClickListener(e -> refresh());

        compareButton = new Button("Compare");
        compareButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        compareButton.setEnabled(false);
        compareButton.addClickListener(e -> onCompare());

        scenarioFilter = new ComboBox<>();
        scenarioFilter.setPlaceholder("All scenarios");
        scenarioFilter.setClearButtonVisible(true);
        scenarioFilter.setWidth("180px");
        scenarioFilter.addValueChangeListener(e -> applyFilters());

        injectionFilter = new Select<>();
        injectionFilter.setItems("All", "ON", "OFF");
        injectionFilter.setValue("All");
        injectionFilter.setWidth("80px");
        injectionFilter.addValueChangeListener(e -> applyFilters());

        var toolbar = new HorizontalLayout(scenarioFilter, injectionFilter, refreshButton, compareButton);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setSpacing(true);
        toolbar.setWidthFull();

        grid = new Grid<>();
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.MULTI);

        grid.addColumn(SimulationRunRecord::scenarioId)
                .setHeader("Scenario")
                .setFlexGrow(2)
                .setSortable(true);

        grid.addColumn(record -> DATE_FORMAT.format(record.completedAt()))
                .setHeader("Date")
                .setWidth("130px")
                .setSortable(true);

        grid.addColumn(record -> String.valueOf(record.turnSnapshots().size()))
                .setHeader("Turns")
                .setWidth("65px")
                .setSortable(true);

        grid.addColumn(record -> "%.0f%%".formatted(record.resilienceRate() * 100))
                .setHeader("Resilience")
                .setWidth("90px")
                .setSortable(true);

        grid.addColumn(record -> record.modelId() != null ? record.modelId() : "")
                .setHeader("Model")
                .setWidth("120px")
                .setSortable(true);

        grid.addColumn(record -> record.injectionEnabled() ? "ON" : "OFF")
                .setHeader("Inj.")
                .setWidth("60px")
                .setSortable(true);

        grid.addComponentColumn(this::createActions)
                .setHeader("")
                .setWidth("120px");

        grid.asMultiSelect().addValueChangeListener(event -> {
            selectedRunIds.clear();
            event.getValue().forEach(r -> selectedRunIds.add(r.runId()));
            compareButton.setEnabled(selectedRunIds.size() == 2);
        });

        grid.addItemClickListener(event -> {
            if (event.getClickCount() == 2) {
                UI.getCurrent().navigate("run?runId=" + event.getItem().runId());
            }
        });

        add(header, toolbar, grid);
        setFlexGrow(1, grid);
    }

    public void refresh() {
        allRuns = runStore.list();
        var scenarios = allRuns.stream()
                .map(SimulationRunRecord::scenarioId)
                .distinct()
                .sorted()
                .toList();
        scenarioFilter.setItems(scenarios);
        applyFilters();
        selectedRunIds.clear();
        compareButton.setEnabled(false);
    }

    private void applyFilters() {
        var filtered = allRuns.stream();

        var scenario = scenarioFilter.getValue();
        if (scenario != null && !scenario.isBlank()) {
            filtered = filtered.filter(r -> r.scenarioId().equals(scenario));
        }

        var injection = injectionFilter.getValue();
        if ("ON".equals(injection)) {
            filtered = filtered.filter(SimulationRunRecord::injectionEnabled);
        } else if ("OFF".equals(injection)) {
            filtered = filtered.filter(r -> !r.injectionEnabled());
        }

        grid.setItems(filtered.toList());
    }

    private HorizontalLayout createActions(SimulationRunRecord record) {
        var inspectButton = new Button(VaadinIcon.SEARCH.create());
        inspectButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        inspectButton.getElement().setAttribute("title", "Inspect");
        inspectButton.addClickListener(e ->
                UI.getCurrent().navigate("run?runId=" + record.runId()));

        var chatButton = new Button(VaadinIcon.CHAT.create());
        chatButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        chatButton.getElement().setAttribute("title", "Start Chat");
        chatButton.addClickListener(e -> {
            var conversationId = simToChatBridge.cloneRunToConversation(record);
            UI.getCurrent().navigate("chat?conversationId=" + conversationId);
        });

        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteButton.getElement().setAttribute("title", "Delete");
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
                record.scenarioId(), DATE_FORMAT.format(record.completedAt())));
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            runStore.delete(record.runId());
            allRuns = allRuns.stream().filter(r -> !r.runId().equals(record.runId())).toList();
            applyFilters();
            Notification.show("Run deleted.", 3000, Notification.Position.BOTTOM_START);
        });
        dialog.open();
    }

    private void onCompare() {
        if (selectedRunIds.size() != 2) {
            return;
        }
        var ids = selectedRunIds.iterator();
        UI.getCurrent().navigate("run?runId=" + ids.next() + "&compare=" + ids.next());
    }
}
