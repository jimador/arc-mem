package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentProgress;

/**
 * Progress panel for ongoing ablation experiment execution.
 * <p>
 * Displays per-cell and per-run progress, an ETA estimate derived from elapsed time,
 * a scrolling log of completed cells, and a cancel button.
 * <p>
 * Drive this panel by calling {@link #updateProgress(ExperimentProgress)} from a
 * background thread via {@code UI.getCurrent().access(...)}.
 * Call {@link #reset()} before starting a new experiment.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>EP1</strong>: {@code startTimeMs} is set on the first call to {@link #updateProgress}
 *       and cleared on {@link #reset()}.</li>
 *   <li><strong>EP2</strong>: ETA is only displayed when at least one cell has started (currentCell >= 1).</li>
 *   <li><strong>EP3</strong>: Completed cells are appended to the log; they are never removed until {@link #reset()}.</li>
 * </ul>
 */
public class ExperimentProgressPanel extends VerticalLayout {

    private final Span cellLabel;
    private final Span runLabel;
    private final ProgressBar progressBar;
    private final Span etaLabel;
    private final VerticalLayout completedCellLog;
    private final Button cancelButton;

    private Runnable cancelCallback;

    /** Wall-clock time in ms when the first progress update arrived. */
    private long startTimeMs = -1L;

    /**
     * Track the last cell index observed, to detect cell transitions.
     * A transition is detected when currentRun resets to 1 and cell index advances.
     */
    private int lastCellIndex = 0;
    private String lastCellCondition = null;
    private String lastCellScenario = null;

    public ExperimentProgressPanel() {
        setPadding(true);
        setSpacing(true);
        setWidthFull();

        var progressTitle = new H4("Experiment Progress");
        progressTitle.addClassName("ar-section-title");

        // --- Cell and run labels ---
        cellLabel = new Span();
        cellLabel.addClassName("ar-bench-progress-label");

        runLabel = new Span();
        runLabel.addClassName("ar-bench-progress-label");

        var labelRow = new HorizontalLayout(cellLabel, runLabel);
        labelRow.setSpacing(true);
        labelRow.setAlignItems(HorizontalLayout.Alignment.CENTER);

        // --- Progress bar ---
        progressBar = new ProgressBar(0, 1, 0);
        progressBar.setWidthFull();

        etaLabel = new Span();
        etaLabel.addClassName("ar-bench-progress-label");

        var progressRow = new HorizontalLayout(progressBar, etaLabel);
        progressRow.setWidthFull();
        progressRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        progressRow.setSpacing(true);
        progressRow.setFlexGrow(1, progressBar);
        progressRow.addClassName("ar-bench-progress");

        // --- Completed cell log ---
        var logTitle = new H4("Completed Cells");
        logTitle.addClassName("ar-section-title--inner");

        completedCellLog = new VerticalLayout();
        completedCellLog.setPadding(false);
        completedCellLog.setSpacing(false);
        completedCellLog.addClassName("ar-experiment-cell-log");

        // --- Cancel button ---
        cancelButton = new Button("Cancel Experiment");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        cancelButton.addClickListener(e -> {
            cancelButton.setText("Cancelling... completing current cell");
            cancelButton.setEnabled(false);
            if (cancelCallback != null) {
                cancelCallback.run();
            }
        });

        add(progressTitle, labelRow, progressRow, logTitle, completedCellLog, cancelButton);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Update all display components from a progress snapshot.
     * <p>
     * On the first call, records the start time for ETA computation.
     * Detects cell transitions (when {@code currentRun == 1} and cell index advances)
     * and appends the prior cell to the completed log.
     *
     * @param progress the latest progress snapshot from the experiment runner
     */
    public void updateProgress(ExperimentProgress progress) {
        if (startTimeMs < 0) {
            startTimeMs = System.currentTimeMillis();
        }

        // Detect cell transition: a new cell has started when run resets to 1 and cell index moved forward.
        if (progress.currentRun() == 1 && progress.currentCell() > lastCellIndex && lastCellIndex > 0) {
            appendCompletedCell(lastCellIndex, progress.totalCells(), lastCellCondition, lastCellScenario);
        }

        lastCellIndex = progress.currentCell();
        lastCellCondition = progress.conditionName();
        lastCellScenario = progress.scenarioId();

        cellLabel.setText("Cell %d/%d: %s x %s".formatted(
                progress.currentCell(),
                progress.totalCells(),
                progress.conditionName(),
                progress.scenarioId()));

        runLabel.setText("Run %d/%d".formatted(progress.currentRun(), progress.totalRuns()));

        // Overall fraction: each cell contributes (1/totalCells), runs subdivide within the cell.
        var totalCells = progress.totalCells();
        var completedCellFraction = (double) (progress.currentCell() - 1) / totalCells;
        var currentCellFraction = (double) (progress.currentRun() - 1) / (progress.totalRuns() * totalCells);
        var fraction = completedCellFraction + currentCellFraction;
        progressBar.setValue(Math.min(1.0, fraction));

        // ETA: elapsed × (1 - fraction) / fraction; EP2: only after at least one cell started.
        if (lastCellIndex >= 1 && fraction > 0) {
            var elapsedMs = System.currentTimeMillis() - startTimeMs;
            var remainingMs = (long) (elapsedMs * (1.0 - fraction) / fraction);
            etaLabel.setText("ETA: %s".formatted(formatDuration(remainingMs)));
        } else {
            etaLabel.setText("");
        }
    }

    /**
     * Clear all progress state and reset to the initial empty display.
     */
    public void reset() {
        startTimeMs = -1L;
        lastCellIndex = 0;
        lastCellCondition = null;
        lastCellScenario = null;

        cellLabel.setText("");
        runLabel.setText("");
        progressBar.setValue(0);
        etaLabel.setText("");
        completedCellLog.removeAll();

        cancelButton.setText("Cancel Experiment");
        cancelButton.setEnabled(true);
    }

    /**
     * Set the callback invoked when the user clicks "Cancel Experiment".
     */
    public void setCancelCallback(Runnable callback) {
        this.cancelCallback = callback;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    // Limitation: completed-cell log does not include factSurvivalRate because ExperimentProgress
    // is intentionally lightweight and does not carry per-metric results.
    private void appendCompletedCell(int cellIndex, int totalCells, String conditionName, String scenarioId) {
        var entry = new Span("Cell %d/%d completed: %s x %s".formatted(
                cellIndex, totalCells, conditionName, scenarioId));
        entry.addClassName("ar-experiment-cell-log-entry");
        completedCellLog.add(entry);
    }

    private String formatDuration(long ms) {
        if (ms <= 0) {
            return "0m 0s";
        }
        var totalSeconds = ms / 1000;
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return "%dm %ds".formatted(minutes, seconds);
    }
}
