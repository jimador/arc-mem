package dev.arcmem.core.assembly.protection;

import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Protects messages backing unpromoted propositions from compaction.
 * Protection priority is {@code (int)(trustScore * 100)} when trust scoring is active,
 * or {@code (int)(confidence * 100)} otherwise.
 * <p>
 * Unpromoted propositions are those with rank == 0 (not yet promoted to unit status).
 */
@Component
public class PropositionContentProtector implements ProtectedContentProvider {

    private static final Logger logger = LoggerFactory.getLogger(PropositionContentProtector.class);

    private final MemoryUnitRepository repository;

    public PropositionContentProtector(MemoryUnitRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ProtectedContent> getProtectedContent(String contextId) {
        var propositions = repository.findByContextIdValue(contextId);
        var result = propositions.stream()
                                 .filter(p -> p.getConfidence() > 0)
                                 .map(p -> {
                                     var priority = (int) (p.getConfidence() * 100);
                                     return new ProtectedContent(
                                             p.getId(),
                                             p.getText(),
                                             priority,
                                             "Proposition confidence=%.2f".formatted(p.getConfidence())
                                     );
                                 })
                                 .toList();
        logger.debug("Protected {} proposition-backed items for context {}", result.size(), contextId);
        return result;
    }
}
