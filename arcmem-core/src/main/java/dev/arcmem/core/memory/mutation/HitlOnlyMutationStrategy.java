package dev.arcmem.core.memory.mutation;
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

import org.springframework.stereotype.Service;

@Service
public final class HitlOnlyMutationStrategy implements MemoryUnitMutationStrategy {

    @Override
    public MutationDecision evaluate(MutationRequest request) {
        return switch (request.source()) {
            case UI -> new MutationDecision.Allow();
            case LLM_TOOL -> new MutationDecision.Deny("LLM-initiated mutation is disabled under HITL-only policy");
            case CONFLICT_RESOLVER -> new MutationDecision.Deny("Conflict-resolver mutation is disabled under HITL-only policy");
        };
    }
}
