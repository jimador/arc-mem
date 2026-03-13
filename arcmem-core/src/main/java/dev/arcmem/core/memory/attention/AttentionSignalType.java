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

public enum AttentionSignalType {

    PRESSURE_SPIKE(Severity.HIGH),
    HEAT_PEAK(Severity.MEDIUM),
    HEAT_DROP(Severity.MEDIUM),
    CLUSTER_DRIFT(Severity.HIGH);

    private final Severity severity;

    AttentionSignalType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    public enum Severity { LOW, MEDIUM, HIGH }
}
