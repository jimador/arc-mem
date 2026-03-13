package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-run mutable state for a single simulation execution.
 * <p>
 * Isolates running/paused/cancel state so that multiple simulation runs
 * (e.g., parallel benchmark reps) do not share mutable fields on
 * {@link SimulationService}. Each {@code runSimulation()} invocation
 * creates its own context.
 */
public final class SimulationRunContext {

    private volatile boolean running;
    private volatile boolean paused;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final String contextId;
    private final String runId;

    public SimulationRunContext(String contextId, String runId) {
        this.contextId = contextId;
        this.runId = runId;
    }

    public void start() {
        running = true;
        paused = false;
        cancelRequested.set(false);
    }

    public void complete() {
        running = false;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void cancel() {
        cancelRequested.set(true);
        paused = false;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public String contextId() {
        return contextId;
    }

    public String runId() {
        return runId;
    }
}
