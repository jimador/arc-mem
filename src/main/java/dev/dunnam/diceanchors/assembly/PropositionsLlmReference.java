package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides unanchored proposition context for system prompt injection.
 */
public class PropositionsLlmReference {

    private static final Logger logger = LoggerFactory.getLogger(PropositionsLlmReference.class);

    private final AnchorRepository repository;
    private final String contextId;
    private final int budget;

    private List<PropositionNode> cachedPropositions;

    public PropositionsLlmReference(AnchorRepository repository, String contextId, int budget) {
        this.repository = repository;
        this.contextId = contextId;
        this.budget = budget;
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
        var content = PromptTemplates.render("prompts/propositions-reference.jinja",
                                             Map.of("propositions", propositions));
        logger.debug("Injected {} propositions for context {}", propositions.size(), contextId);
        return content;
    }

    public List<PropositionNode> getPropositions() {
        ensureLoaded();
        return cachedPropositions;
    }

    public void refresh() {
        cachedPropositions = null;
    }

    private void ensureLoaded() {
        if (cachedPropositions != null) {
            return;
        }
        var loaded = repository.findActiveUnanchoredPropositions(contextId, budget);
        cachedPropositions = loaded != null ? loaded : List.of();
    }
}
