package dev.arcmem.core.memory.mutation;

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
