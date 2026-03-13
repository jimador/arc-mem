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

/**
 * Difficulty tier for drift strategies, ordered from least to most sophisticated.
 * <p>
 * Invariant: {@code level} values are monotonically increasing from BASIC (1) to EXPERT (4).
 */
public enum StrategyTier {
    BASIC(1),
    INTERMEDIATE(2),
    ADVANCED(3),
    EXPERT(4);

    private final int level;

    StrategyTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtOrBelow(StrategyTier other) {
        return this.level <= other.level;
    }
}
