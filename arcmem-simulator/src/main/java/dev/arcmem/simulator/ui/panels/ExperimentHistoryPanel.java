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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.arcmem.simulator.benchmark.BenchmarkReport;
import dev.arcmem.simulator.benchmark.ExperimentReport;
import dev.arcmem.simulator.history.RunHistoryStore;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel listing all persisted experiment reports, sorted newest-first.
 * <p>
 * Each row shows the experiment name, creation date, configuration summary, and
 * the grand-mean {@code factSurvivalRate} as a primary quality signal. Users can
 * load a report into the active view or delete it with a confirmation step.
 * <p>
 * Callbacks are injected via {@link #setLoadCallback} and {@link #setDeleteCallback}.
 * Call {@link #refresh()} to reload from {@link RunHistoryStore}.
 */
public class ExperimentHistoryPanel extends VerticalLayout {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RunHistoryStore runHistoryStore;

    private Consumer<ExperimentReport> loadCallback;
    private Consumer<String> deleteCallback;

    public ExperimentHistoryPanel(RunHistoryStore runHistoryStore) {
        this.runHistoryStore = runHistoryStore;

        setPadding(true);
        setSpacing(true);
        setWidthFull();
        addClassName("ar-experiment-history-panel");
    }

    /**
     * Reload the experiment list from the store and re-render all rows.
     * Rows are displayed newest-first (RunHistoryStore.listExperimentReports() is
     * already sorted descending by createdAt).
     */
    public void refresh() {
        removeAll();

        List<ExperimentReport> reports;
        try {
            reports = runHistoryStore.listExperimentReports();
        } catch (Exception e) {
            var errorNotice = new Span("Failed to load experiment history.");
            errorNotice.addClassName("ar-history-empty");
            add(errorNotice);
            return;
        }

        if (reports.isEmpty()) {
            var emptyNotice = new Span("No experiment reports saved yet.");
            emptyNotice.addClassName("ar-history-empty");
            add(emptyNotice);
            return;
        }

        for (var report : reports) {
            add(buildRow(report));
        }
    }

    /**
     * Set the callback invoked when the user clicks "Load" on a row.
     * Receives the full {@link ExperimentReport}.
     */
    public void setLoadCallback(Consumer<ExperimentReport> callback) {
        this.loadCallback = callback;
    }

    /**
     * Set the callback invoked when the user confirms deletion of a row.
     * Receives the {@link ExperimentReport#reportId()}.
     */
    public void setDeleteCallback(Consumer<String> callback) {
        this.deleteCallback = callback;
    }

    private Div buildRow(ExperimentReport report) {
        var row = new Div();
        row.addClassName("ar-experiment-row");

        var meta = buildMetaColumn(report);
        var actions = buildActionButtons(report);

        var layout = new HorizontalLayout(meta, actions);
        layout.setWidthFull();
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        layout.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        layout.setSpacing(true);

        row.add(layout);
        return row;
    }

    private VerticalLayout buildMetaColumn(ExperimentReport report) {
        var meta = new VerticalLayout();
        meta.setPadding(false);
        meta.setSpacing(false);
        meta.addClassName("ar-experiment-meta");

        var nameSpan = new Span(report.experimentName());
        nameSpan.addClassName("ar-experiment-name");

        var dateSpan = new Span(DATE_FMT.format(report.createdAt()));
        dateSpan.addClassName("ar-experiment-date");

        var configText = "%d conditions, %d scenarios, %d reps".formatted(
                report.conditions().size(),
                report.scenarioIds().size(),
                report.repetitionsPerCell());
        var configSpan = new Span(configText);
        configSpan.addClassName("ar-experiment-config");

        var primaryMetric = computePrimaryMetric(report);
        var metricSpan = new Span("Fact survival: %.1f%%".formatted(primaryMetric));
        metricSpan.addClassName("ar-experiment-primary-metric");
        var health = primaryMetric >= 80 ? "good" : primaryMetric >= 50 ? "warn" : "bad";
        metricSpan.getElement().setAttribute("data-health", health);

        if (report.cancelled()) {
            var cancelledBadge = new Span("CANCELLED");
            cancelledBadge.addClassName("ar-bench-warning-badge");
            meta.add(nameSpan, dateSpan, configSpan, metricSpan, cancelledBadge);
        } else {
            meta.add(nameSpan, dateSpan, configSpan, metricSpan);
        }

        return meta;
    }

    private HorizontalLayout buildActionButtons(ExperimentReport report) {
        var loadButton = new Button("Load");
        loadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        loadButton.addClickListener(e -> {
            if (loadCallback != null) {
                loadCallback.accept(report);
            }
        });

        var deleteButton = new Button("Delete");
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        deleteButton.addClickListener(e -> openDeleteConfirmation(report));

        var buttons = new HorizontalLayout(loadButton, deleteButton);
        buttons.setSpacing(true);
        buttons.addClassName("ar-experiment-actions");
        return buttons;
    }

    private void openDeleteConfirmation(ExperimentReport report) {
        var dialog = new ConfirmDialog();
        dialog.setHeader("Delete experiment?");
        dialog.setText("Delete \"%s\"? This cannot be undone.".formatted(report.experimentName()));
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(e -> {
            if (deleteCallback != null) {
                deleteCallback.accept(report.reportId());
            }
            refresh();
        });

        dialog.open();
    }

    /**
     * Compute the grand-mean {@code factSurvivalRate} across all cell reports.
     * Returns 0.0 if no cell reports have survival data.
     */
    private double computePrimaryMetric(ExperimentReport report) {
        if (report.cellReports().isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;

        for (BenchmarkReport cell : report.cellReports().values()) {
            var stats = cell.metricStatistics().get("factSurvivalRate");
            if (stats != null && !Double.isNaN(stats.mean())) {
                sum += stats.mean();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }
}
