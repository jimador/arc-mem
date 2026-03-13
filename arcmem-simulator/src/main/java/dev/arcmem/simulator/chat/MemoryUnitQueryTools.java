package dev.arcmem.simulator.chat;
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

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import dev.arcmem.core.config.ArcMemProperties.RetrievalConfig;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@MatryoshkaTools(name = "unit-query-tools",
        description = "Read-only tools for querying established facts (units)",
        removeOnInvoke = false)
public record MemoryUnitQueryTools(
        ArcMemEngine engine,
        MemoryUnitRepository repository,
        RelevanceScorer scorer,
        String contextId,
        RetrievalConfig config,
        AtomicInteger toolCallCounter
) {
    public MemoryUnitQueryTools(ArcMemEngine engine, MemoryUnitRepository repository,
                            RelevanceScorer scorer, String contextId) {
        this(engine, repository, scorer, contextId, null, new AtomicInteger(0));
    }

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitQueryTools.class);
    private static final int SEARCH_TOP_K = 10;
    private static final double SEARCH_THRESHOLD = 0.5;

    @LlmTool(description = """
            Search for established facts (units) by subject or keyword. \
            Returns units with their ID, text, rank (100–900), authority level \
            (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
            Use this before asserting facts to verify what is already known. \
            Returns an empty list if no matching units exist.""")
    public List<MemoryUnitSummary> queryFacts(
            @LlmTool.Param(description = "Subject or keyword to search for in established facts") String subject) {
        logger.info("LLM tool call: queryFacts with subject={}", subject);
        var summaries = repository.semanticSearch(subject, contextId, SEARCH_TOP_K, SEARCH_THRESHOLD)
                                  .stream()
                                  .map(scored -> repository.findPropositionNodeById(scored.id())
                                                           .filter(PropositionNode::isUnit)
                                                           .map(node -> new MemoryUnitSummary(
                                                                   node.getId(),
                                                                   node.getText(),
                                                                   node.getRank(),
                                                                   node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name(),
                                                                   node.isPinned(),
                                                                   node.getConfidence()
                                                           ))
                                                           .orElse(null))
                                  .filter(Objects::nonNull)
                                  .toList();
        logger.info("Tool result: queryFacts returned {} units", summaries.size());
        return summaries;
    }

    @LlmTool(description = """
            List all currently active units for the current conversation context. \
            Returns each unit's ID, text, rank (100–900), authority \
            (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
            Units are ordered highest-rank first. Use this to review the full known-facts \
            baseline before making assertions or when auditing context consistency.""")
    public List<MemoryUnitSummary> listUnits() {
        logger.info("LLM tool call: listUnits for context={}", contextId);
        var summaries = engine.inject(contextId).stream()
                              .map(MemoryUnitQueryTools::toSummary)
                              .toList();
        logger.info("Tool result: listUnits returned {} units", summaries.size());
        return summaries;
    }

    @LlmTool(description = """
            Retrieve established facts (units) most relevant to a specific \
            topic or question. Returns units scored and ranked by relevance to your query. \
            Use this when you need grounding on a specific topic that may not be in your \
            baseline context. Each result includes the fact text, authority level, memory tier, \
            and a relevance score (0.0-1.0).""")
    public List<ScoredMemoryUnit> retrieveUnits(
            @LlmTool.Param(description = "Topic or question to find relevant units for") String query) {
        logger.info("LLM tool call: retrieveUnits with query={}", query);
        var units = engine.inject(contextId);

        if (units.isEmpty()) {
            logger.info("Tool result: no units available for retrieval");
            return List.of();
        }

        var topK = config != null ? config.toolTopK() : 5;
        var minRelevance = config != null ? config.minRelevance() : 0.0;

        var scored = scorer.scoreByRelevance(query, units, config);

        var results = scored.stream()
                            .filter(sa -> sa.relevanceScore() >= minRelevance)
                            .limit(topK)
                            .toList();

        var callCount = toolCallCounter.incrementAndGet();
        Span.current().setAttribute("retrieval.tool_call_count", callCount);

        logger.info("Tool result: retrieveUnits returned {} results (top-k={}, minRelevance={})",
                    results.size(), topK, minRelevance);
        return results;
    }

    private static MemoryUnitSummary toSummary(MemoryUnit unit) {
        return new MemoryUnitSummary(
                unit.id(),
                unit.text(),
                unit.rank(),
                unit.authority().name(),
                unit.pinned(),
                unit.confidence()
        );
    }
}
