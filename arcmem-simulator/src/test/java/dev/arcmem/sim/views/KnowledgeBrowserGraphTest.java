package dev.arcmem.simulator.ui.panels;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.EntityMentionEdge;
import dev.arcmem.core.persistence.EntityMentionGraph;
import dev.arcmem.core.persistence.EntityMentionGraphFilter;
import dev.arcmem.core.persistence.EntityMentionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Knowledge browser graph")
class KnowledgeBrowserGraphTest {

    @Mock private ArcMemEngine arcMemEngine;
    @Mock private MemoryUnitRepository contextUnitRepository;

    @Test
    @DisplayName("applies graph control filters when context refreshes")
    void appliesGraphControlFiltersOnRefresh() {
        when(arcMemEngine.inject(any())).thenReturn(List.of());
        when(contextUnitRepository.findByContextIdValue(any())).thenReturn(List.of());
        when(contextUnitRepository.findEntityMentionGraph(eq("sim-ctx"), any(EntityMentionGraphFilter.class)))
                .thenReturn(EntityMentionGraph.empty());

        var panel = new KnowledgeBrowserPanel(arcMemEngine, contextUnitRepository);
        panel.setContextId("sim-ctx");

        var minEdgeWeight = field(panel, "minEdgeWeightFilter", com.vaadin.flow.component.textfield.NumberField.class);
        var graphScope = field(panel, "graphScopeFilter", com.vaadin.flow.component.select.Select.class);

        minEdgeWeight.setValue(4.0);
        graphScope.setValue("ALL_PROPOSITIONS");

        var captor = ArgumentCaptor.forClass(EntityMentionGraphFilter.class);
        verify(contextUnitRepository, atLeast(2)).findEntityMentionGraph(eq("sim-ctx"), captor.capture());
        var lastFilter = captor.getAllValues().getLast();

        assertThat(lastFilter.minEdgeWeight()).isEqualTo(4);
        assertThat(lastFilter.activeOnly()).isFalse();
    }

    @Test
    @DisplayName("supports node focus and empty state behavior")
    void supportsNodeFocusAndEmptyState() {
        var view = new EntityMentionNetworkView();
        view.setGraph(EntityMentionGraph.empty());

        assertThat(view.renderedNodeCount()).isEqualTo(0);
        assertThat(view.renderedEdgeCount()).isEqualTo(0);

        var graph = new EntityMentionGraph(
                List.of(
                        new EntityMentionNode("person-1", "Alice", "Person", 3, 2),
                        new EntityMentionNode("place-1", "Castle", "Location", 2, 2),
                        new EntityMentionNode("item-1", "Orb", "Item", 1, 1)),
                List.of(
                        new EntityMentionEdge("person-1", "place-1", 2, List.of("p1", "p2")),
                        new EntityMentionEdge("person-1", "item-1", 1, List.of("p3"))));

        view.setGraph(graph);

        assertThat(view.renderedNodeCount()).isEqualTo(3);
        assertThat(view.renderedEdgeCount()).isEqualTo(2);
        assertThat(view.focusEntity("Alice")).isTrue();
        assertThat(view.selectedEntityId()).isEqualTo("person-1");
        assertThat(view.focusEntity("NoSuchEntity")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to access field " + name, e);
        }
    }
}
