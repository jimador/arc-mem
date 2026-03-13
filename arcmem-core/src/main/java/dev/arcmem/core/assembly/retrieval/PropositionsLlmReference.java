package dev.arcmem.core.assembly.retrieval;
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

import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides non-unit proposition context for system prompt injection.
 * <p>
 * Cache invalidation is event-driven via {@link MemoryUnitCacheInvalidator}: whenever
 * a lifecycle event is published for this context, the next call to
 * {@link #getContent()} or {@link #getPropositions()} will reload from the repository.
 */
public class PropositionsLlmReference {

    private static final Logger logger = LoggerFactory.getLogger(PropositionsLlmReference.class);

    private final MemoryUnitRepository repository;
    private final String contextId;
    private final int budget;
    private final MemoryUnitCacheInvalidator cacheInvalidator;

    private List<PropositionNode> cachedPropositions;

    public PropositionsLlmReference(MemoryUnitRepository repository, String contextId, int budget) {
        this(repository, contextId, budget, null);
    }

    public PropositionsLlmReference(MemoryUnitRepository repository, String contextId, int budget,
                                    MemoryUnitCacheInvalidator cacheInvalidator) {
        this.repository = repository;
        this.contextId = contextId;
        this.budget = budget;
        this.cacheInvalidator = cacheInvalidator;
    }

    public String getContent() {
        ensureLoaded();
        if (cachedPropositions.isEmpty()) {
            return "";
        }

        var propositions = cachedPropositions.stream()
                                             .map(p -> Map.<String, Object>of(
                                                     "id", p.getId(),
                                                     "text", p.getText(),
                                                     "confidence", String.format(Locale.ROOT, "%.2f", p.getConfidence())))
                                             .toList();
        var content = PromptTemplates.render(PromptPathConstants.PROPOSITIONS_REFERENCE,
                                             Map.of("propositions", propositions));
        logger.debug("Injected {} propositions for context {}", propositions.size(), contextId);
        return content;
    }

    public List<PropositionNode> getPropositions() {
        ensureLoaded();
        return cachedPropositions;
    }

    /**
     * Clears cached state so the next access reloads from the repository.
     * If an {@link MemoryUnitCacheInvalidator} is present, marks the context clean.
     */
    public void refresh() {
        if (cacheInvalidator != null) {
            cacheInvalidator.markClean(contextId);
        }
        cachedPropositions = null;
    }

    private void ensureLoaded() {
        if (cachedPropositions != null) {
            if (cacheInvalidator != null && cacheInvalidator.isDirty(contextId)) {
                cacheInvalidator.markClean(contextId);
                cachedPropositions = null;
            } else {
                return;
            }
        }
        var loaded = repository.findActiveUnpromotedPropositions(contextId, budget);
        cachedPropositions = loaded != null ? loaded : List.of();
    }
}
