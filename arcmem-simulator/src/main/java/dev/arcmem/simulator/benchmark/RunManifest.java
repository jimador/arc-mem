package dev.arcmem.simulator.benchmark;

import java.time.Instant;
import java.util.List;

public record RunManifest(
        String configHash,
        String gitCommit,
        Instant startedAt,
        Instant completedAt,
        long wallClockMs,
        String javaVersion,
        List<String> conditions,
        List<String> scenarios,
        int repetitionsPerCell,
        int totalCells,
        int totalRuns,
        boolean cancelled
) {}
