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

import org.drivine.mapper.RowMapper;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Maps Neo4j vector search results to proposition ID and score pairs.
 * Uses Drivine's RowMapper interface which properly handles Neo4j driver value types.
 */
class PropositionSimilarityMapper implements RowMapper<PropositionSimilarityResult> {

    @Override
    public @NonNull PropositionSimilarityResult map(@NonNull Map<String, ?> row) {
        var id = (String) row.get("id");
        var score = ((Number) row.get("score")).doubleValue();
        return new PropositionSimilarityResult(id, score);
    }
}
