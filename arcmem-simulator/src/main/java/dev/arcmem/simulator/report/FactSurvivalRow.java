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

import java.util.Map;

/**
 * Per-fact survival data across all conditions for a single ground truth fact.
 *
 * @param factId           stable fact identifier from scenario ground truth
 * @param factText         human-readable fact text
 * @param conditionResults condition name to survival result
 */
public record FactSurvivalRow(
        String factId,
        String factText,
        Map<String, FactConditionResult> conditionResults) {
}
