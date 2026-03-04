package dev.dunnam.diceanchors.persistence;

import com.embabel.common.ai.model.EmbeddingService;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorRepository entity mention graph")
class AnchorRepositoryEntityMentionGraphTest {

    @Mock private GraphObjectManager graphObjectManager;
    @Mock private PersistenceManager persistenceManager;
    @Mock private EmbeddingService embeddingService;

    private AnchorRepository repository;

    @BeforeEach
    void setUp() {
        var properties = new DiceAnchorsProperties(
                null,
                null,
                null,
                new DiceAnchorsProperties.PersistenceConfig(false),
                null,
                null,
                null,
                new DiceAnchorsProperties.AssemblyConfig(0, false, dev.dunnam.diceanchors.assembly.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null);
        repository = new AnchorRepository(graphObjectManager, persistenceManager, embeddingService, properties);
    }

    @Test
    @DisplayName("derives weighted co-mention edges from query rows")
    void derivesWeightedCoMentionEdgesFromRows() {
        var nodes = List.<Map<String, ?>>of(
                Map.of("entityId", "person-1", "label", "Alice", "entityType", "Person", "mentionCount", 5, "propositionCount", 3),
                Map.of("entityId", "place-1", "label", "Castle", "entityType", "Location", "mentionCount", 3, "propositionCount", 2));
        var edges = List.<Map<String, ?>>of(
                Map.of("sourceEntityId", "person-1", "targetEntityId", "place-1", "weight", 3, "propositionIds", List.of("p1", "p2", "p3")));

        when(persistenceManager.query(any(QuerySpecification.class))).thenReturn(nodes, edges);

        var graph = repository.findEntityMentionGraph("sim-abc123", new EntityMentionGraphFilter(2, null, true));

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst().weight()).isEqualTo(3);
        assertThat(graph.edges().getFirst().propositionIds()).containsExactly("p1", "p2", "p3");

        var captor = ArgumentCaptor.forClass(QuerySpecification.class);
        verify(persistenceManager, times(2)).query(captor.capture());
        var firstParams = captor.getAllValues().get(0).getParameters();
        var secondParams = captor.getAllValues().get(1).getParameters();
        assertThat(firstParams).containsEntry("contextId", "sim-abc123");
        assertThat(firstParams).containsEntry("activeOnly", true);
        assertThat(secondParams).containsEntry("minEdgeWeight", 2);
    }

    @Test
    @DisplayName("returns empty graph for blank context without querying")
    void returnsEmptyGraphForBlankContext() {
        var graph = repository.findEntityMentionGraph("   ", EntityMentionGraphFilter.defaults());

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        verifyNoInteractions(persistenceManager);
    }

    @Test
    @DisplayName("handles malformed rows and computes fallback ids and weights")
    void handlesMalformedRowsAndFallbackIdentity() {
        var nodes = List.<Map<String, ?>>of(
                Map.of("label", "Unknown Mage", "entityType", "Person", "mentionCount", 2, "propositionCount", 1),
                Map.of("entityId", "artifact-1", "label", "Orb", "entityType", "Item", "mentionCount", 4, "propositionCount", 2));
        var edges = List.<Map<String, ?>>of(
                Map.of("sourceEntityId", "artifact-1", "targetEntityId", "artifact-1", "weight", 1, "propositionIds", List.of("p1")),
                Map.of("sourceEntityId", "artifact-1", "targetEntityId", "unresolved:person:unknown mage", "propositionIds", List.of("p2", "p2")));

        when(persistenceManager.query(any(QuerySpecification.class))).thenReturn(nodes, edges);

        var graph = repository.findEntityMentionGraph("sim-xyz", EntityMentionGraphFilter.defaults());

        assertThat(graph.nodes()).extracting(EntityMentionNode::entityId)
                                 .anyMatch(id -> id.startsWith("unresolved:person:"));
        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst().weight()).isEqualTo(1);
        assertThat(graph.edges().getFirst().propositionIds()).containsExactly("p2");
    }

    @Test
    @DisplayName("passes entity type and active filters to both node and edge queries")
    void passesEntityTypeAndActiveFilters() {
        when(persistenceManager.query(any(QuerySpecification.class))).thenReturn(List.of(), List.of());

        repository.findEntityMentionGraph("sim-filter", new EntityMentionGraphFilter(4, "Person", true));

        var captor = ArgumentCaptor.forClass(QuerySpecification.class);
        verify(persistenceManager, times(2)).query(captor.capture());
        var firstParams = captor.getAllValues().get(0).getParameters();
        var secondParams = captor.getAllValues().get(1).getParameters();

        assertThat(firstParams).containsEntry("entityType", "Person");
        assertThat(firstParams).containsEntry("activeOnly", true);
        assertThat(secondParams).containsEntry("entityType", "Person");
        assertThat(secondParams).containsEntry("activeOnly", true);
        assertThat(secondParams).containsEntry("minEdgeWeight", 4);
    }
}
