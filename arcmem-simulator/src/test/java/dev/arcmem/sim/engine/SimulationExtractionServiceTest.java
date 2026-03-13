package dev.arcmem.simulator.engine;
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

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.pipeline.PropositionResults;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import dev.arcmem.core.extraction.SemanticUnitPromoter;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationExtractionService")
class SimulationExtractionServiceTest {

    @Mock private PropositionPipeline pipeline;
    @Mock private PropositionRepository propositionRepository;
    @Mock private MemoryUnitRepository contextUnitRepository;
    @Mock private SemanticUnitPromoter promoter;
    @Mock private DataDictionary dataDictionary;
    @Mock private EntityResolver entityResolver;
    @Mock private ObjectProvider<NamedEntityDataRepository> namedEntityRepositoryProvider;
    @Mock private NamedEntityDataRepository namedEntityRepository;
    @Mock private PropositionResults propositionResults;
    @Mock private Proposition proposition;

    private SimulationExtractionService service;

    @BeforeEach
    void setUp() {
        service = new SimulationExtractionService(
                pipeline,
                propositionRepository,
                contextUnitRepository,
                promoter,
                dataDictionary,
                entityResolver,
                namedEntityRepositoryProvider);
    }

    @Test
    @DisplayName("persists propositions even when named-entity repository is unavailable")
    void persistsWithoutNamedEntityRepository() {
        when(pipeline.process(anyList(), any())).thenReturn(propositionResults);
        when(propositionResults.propositionsToPersist()).thenReturn(List.of(proposition));
        when(proposition.getId()).thenReturn("p1");
        when(proposition.getText()).thenReturn("Fact text");
        when(promoter.batchEvaluateAndPromoteWithOutcome(eq("ctx-1"), anyList()))
                .thenReturn(new SemanticUnitPromoter.PromotionOutcome(1, 0));
        when(namedEntityRepositoryProvider.getIfAvailable()).thenReturn(null);
        when(contextUnitRepository.assignContextIds(List.of("p1"), "ctx-1")).thenReturn(1);

        var result = service.extract("ctx-1", "DM response");

        verify(propositionRepository).saveAll(List.of(proposition));
        verify(contextUnitRepository).assignContextIds(List.of("p1"), "ctx-1");
        verify(promoter).batchEvaluateAndPromoteWithOutcome(eq("ctx-1"), anyList());
        assertThat(result.extractedCount()).isEqualTo(1);
        assertThat(result.promotedCount()).isEqualTo(1);
        assertThat(result.degradedConflictCount()).isZero();
        assertThat(result.extractedTexts()).containsExactly("Fact text");
    }

    @Test
    @DisplayName("uses full DICE persist path when named-entity repository is available")
    void usesFullPersistPathWhenNamedEntityRepositoryAvailable() {
        when(pipeline.process(anyList(), any())).thenReturn(propositionResults);
        when(propositionResults.propositionsToPersist()).thenReturn(List.of(proposition));
        when(proposition.getId()).thenReturn("p1");
        when(proposition.getText()).thenReturn("Fact text");
        when(promoter.batchEvaluateAndPromoteWithOutcome(eq("ctx-1"), anyList()))
                .thenReturn(new SemanticUnitPromoter.PromotionOutcome(0, 2));
        when(namedEntityRepositoryProvider.getIfAvailable()).thenReturn(namedEntityRepository);
        when(contextUnitRepository.assignContextIds(List.of("p1"), "ctx-1")).thenReturn(1);

        var result = service.extract("ctx-1", "DM response");

        verify(propositionResults).persist(propositionRepository, namedEntityRepository);
        verify(propositionRepository, never()).saveAll(any(java.util.Collection.class));
        verify(contextUnitRepository).assignContextIds(List.of("p1"), "ctx-1");
        assertThat(result.extractedCount()).isEqualTo(1);
        assertThat(result.promotedCount()).isZero();
        assertThat(result.degradedConflictCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns empty result when full persist throws")
    void returnsEmptyWhenFullPersistThrows() {
        when(pipeline.process(anyList(), any())).thenReturn(propositionResults);
        when(propositionResults.propositionsToPersist()).thenReturn(List.of(proposition));
        when(namedEntityRepositoryProvider.getIfAvailable()).thenReturn(namedEntityRepository);
        org.mockito.Mockito.doThrow(new RuntimeException("persist failed"))
                .when(propositionResults).persist(propositionRepository, namedEntityRepository);

        var result = service.extract("ctx-1", "DM response");

        verify(propositionResults).persist(propositionRepository, namedEntityRepository);
        verify(promoter, never()).batchEvaluateAndPromoteWithOutcome(any(), any());
        assertThat(result.extractedCount()).isZero();
        assertThat(result.promotedCount()).isZero();
    }
}
