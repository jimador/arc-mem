package dev.arcmem.simulator.report;
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

import java.util.OptionalInt;

/**
 * Survival result for a single fact under a single condition.
 *
 * @param survived       number of runs where this fact was not contradicted
 * @param total          total runs examined
 * @param firstDriftTurn earliest turn where contradiction occurred; empty if never
 */
public record FactConditionResult(int survived, int total, OptionalInt firstDriftTurn) {
}
