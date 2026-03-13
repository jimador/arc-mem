package dev.arcmem.core.memory.attention;
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

import java.time.Duration;
import java.util.List;

/**
 * Frozen point-in-time view of attention metrics.
 * Constructed from a single window or aggregated from a cluster of windows.
 */
public record AttentionSnapshot(
        double heatScore,
        double pressureScore,
        double burstFactor,
        int conflictCount,
        int reinforcementCount,
        int totalEventCount,
        Duration windowDuration,
        List<String> unitIds
) {
    public AttentionSnapshot {
        unitIds = List.copyOf(unitIds);
    }

    public static AttentionSnapshot of(AttentionWindow window, int maxExpectedEvents) {
        return new AttentionSnapshot(
                window.heatScore(maxExpectedEvents),
                window.pressureScore(),
                window.burstFactor(),
                window.conflictCount(),
                window.reinforcementCount(),
                window.totalEventCount(),
                Duration.between(window.windowStart(), window.lastEventAt()),
                List.of(window.unitId())
        );
    }

    public static AttentionSnapshot ofCluster(List<AttentionWindow> windows, int maxExpectedEvents) {
        if (windows.isEmpty()) {
            return new AttentionSnapshot(0.0, 0.0, 1.0, 0, 0, 0, Duration.ZERO, List.of());
        }
        var ids = windows.stream().map(AttentionWindow::unitId).toList();
        var avgHeat = windows.stream().mapToDouble(w -> w.heatScore(maxExpectedEvents)).average().orElse(0.0);
        var avgPressure = windows.stream().mapToDouble(AttentionWindow::pressureScore).average().orElse(0.0);
        var avgBurst = windows.stream().mapToDouble(AttentionWindow::burstFactor).average().orElse(1.0);
        var totalConflicts = windows.stream().mapToInt(AttentionWindow::conflictCount).sum();
        var totalReinforcements = windows.stream().mapToInt(AttentionWindow::reinforcementCount).sum();
        var totalEvents = windows.stream().mapToInt(AttentionWindow::totalEventCount).sum();
        var maxDuration = windows.stream()
                .map(w -> Duration.between(w.windowStart(), w.lastEventAt()))
                .reduce(Duration.ZERO, (a, b) -> a.compareTo(b) > 0 ? a : b);
        return new AttentionSnapshot(avgHeat, avgPressure, avgBurst,
                totalConflicts, totalReinforcements, totalEvents, maxDuration, ids);
    }
}
