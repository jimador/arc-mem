package dev.arcmem.core.assembly.protection;

import dev.arcmem.core.memory.engine.ArcMemEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Protects messages backing active units from compaction.
 * Protection priority equals the unit's rank (higher rank = higher protection).
 */
@Component
public class MemoryUnitContentProtector implements ProtectedContentProvider {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitContentProtector.class);

    private final ArcMemEngine arcMemEngine;

    public MemoryUnitContentProtector(ArcMemEngine arcMemEngine) {
        this.arcMemEngine = arcMemEngine;
    }

    @Override
    public List<ProtectedContent> getProtectedContent(String contextId) {
        var units = arcMemEngine.inject(contextId);
        var result = units.stream()
                          .map(unit -> new ProtectedContent(
                                  unit.id(),
                                  unit.text(),
                                  unit.rank(),
                                  "Active unit [%s] rank=%d".formatted(unit.authority().name(), unit.rank())
                          ))
                          .toList();
        logger.debug("Protected {} unit-backed items for context {}", result.size(), contextId);
        return result;
    }
}
