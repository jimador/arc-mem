package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides unanchored proposition context for system prompt injection.
 * <p>
 * Cache invalidation is event-driven via {@link AnchorCacheInvalidator}: whenever
 * a lifecycle event is published for this context, the next call to
 * {@link #getContent()} or {@link #getPropositions()} will reload from the repository.
 */
public class PropositionsLlmReference {

    private static final Logger logger = LoggerFactory.getLogger(PropositionsLlmReference.class);

    private final AnchorRepository repository;
    private final String contextId;
    private final int budget;
    private final AnchorCacheInvalidator cacheInvalidator;

    private List<PropositionNode> cachedPropositions;

    public PropositionsLlmReference(AnchorRepository repository, String contextId, int budget) {
        this(repository, contextId, budget, null);
    }

    public PropositionsLlmReference(AnchorRepository repository, String contextId, int budget,
                                    AnchorCacheInvalidator cacheInvalidator) {
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
     * Invalidate cache for next call.
     * If an {@link AnchorCacheInvalidator} is present, marks the context clean
     * and clears cached state so the next access reloads from the repository.
     */
    public void refresh() {
        if (cacheInvalidator != null) {
            cacheInvalidator.markClean(contextId);
        }
        cachedPropositions = null;
    }

    private void ensureLoaded() {
        if (cachedPropositions != null) {
            // Check event-driven invalidation: if dirty, evict the cache and reload.
            if (cacheInvalidator != null && cacheInvalidator.isDirty(contextId)) {
                cacheInvalidator.markClean(contextId);
                cachedPropositions = null;
            } else {
                return;
            }
        }
        var loaded = repository.findActiveUnanchoredPropositions(contextId, budget);
        cachedPropositions = loaded != null ? loaded : List.of();
    }
}
