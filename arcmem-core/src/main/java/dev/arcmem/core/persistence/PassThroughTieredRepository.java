package dev.arcmem.core.persistence;
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

import dev.arcmem.core.config.ArcMemProperties;

import java.util.List;

/**
 * No-op tiered repository that delegates directly to {@link ArcMemEngine} without caching
 * or tier filtering. Used as a fallback when {@code arc-mem.tiered-storage.enabled}
 * is not set, so that consumers can depend on {@link TieredMemoryUnitRepository} unconditionally.
 */
public class PassThroughTieredRepository extends TieredMemoryUnitRepository {

    private final ArcMemEngine passEngine;

    public PassThroughTieredRepository(ArcMemEngine engine, ArcMemProperties properties) {
        super(engine, null, properties);
        this.passEngine = engine;
    }

    @Override
    public List<MemoryUnit> findActiveUnitsForAssembly(String contextId) {
        return passEngine.inject(contextId);
    }

    @Override
    public List<MemoryUnit> findAllTiersForContext(String contextId) {
        return passEngine.findByContext(contextId);
    }
}
