package dev.arcmem.core.assembly.compliance;
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


/**
 * A single unit compliance violation detected during post-generation validation.
 *
 * @param unitId        Neo4j node ID of the violated unit
 * @param unitText      proposition text of the violated unit (for readability in logs/UI)
 * @param unitAuthority authority level of the violated unit; drives action escalation
 * @param description     human-readable description of what the violation is
 * @param confidence      validator confidence in this violation (0.0–1.0); LLM-sourced
 */
public record ComplianceViolation(
        String unitId,
        String unitText,
        Authority unitAuthority,
        String description,
        double confidence
) {}
