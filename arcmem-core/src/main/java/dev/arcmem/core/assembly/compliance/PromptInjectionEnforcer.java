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

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Zero-cost {@link ComplianceEnforcer} that trusts the LLM to comply with injected units.
 * <p>
 * Preserves the existing behavior of {@link ArcMemLlmReference}: units are formatted
 * into the system prompt with authority-tiered compliance directives, and no post-generation
 * verification is performed. Zero overhead, zero LLM calls beyond the primary generation.
 * <p>
 * The active primary enforcer is selected by {@code ComplianceEnforcerFactory} based on
 * the configured {@code enforcement-strategy}. The default is PROMPT_ONLY, which delegates
 * to this implementation.
 */
@Component
public class PromptInjectionEnforcer implements ComplianceEnforcer {

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        return ComplianceResult.compliant(Duration.ZERO);
    }
}
