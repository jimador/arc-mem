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

import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;
import dev.arcmem.simulator.ui.controllers.*;
import dev.arcmem.simulator.ui.views.*;

import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.EntityMentionGraph;
import dev.arcmem.core.persistence.EntityMentionGraphFilter;
import dev.arcmem.simulator.engine.SimulationProgress;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Vaadin panel for browsing propositions, units, and entity mention networks
 * within a simulation context.
 */
public class KnowledgeBrowserPanel extends VerticalLayout implements SimulationProgressListener {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBrowserPanel.class);
    private static final int SEARCH_MIN_LENGTH = 3;
    private static final int SEARCH_TOP_K = 20;
    private static final double SEARCH_SIMILARITY_THRESHOLD = 0.5;

    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository contextUnitRepository;

    private @Nullable String contextId;

    private final Tabs tabs;
    private final Tab propositionsTab;
    private final Tab unitsTab;
    private final Tab graphTab;
    private final VerticalLayout propositionsContent;
    private final VerticalLayout unitsContent;
    private final VerticalLayout graphContent;
    private final VerticalLayout searchResultsContent;

    private final Select<String> statusFilter;
    private final NumberField minConfidenceFilter;

    private final Select<String> authorityFilter;
    private final NumberField minRankFilter;
    private final NumberField maxRankFilter;

    private final NumberField minEdgeWeightFilter;
    private final Select<String> entityTypeFilter;
    private final Select<String> graphScopeFilter;
    private final Button resetGraphLayoutButton;
    private final EntityMentionNetworkView entityMentionNetworkView;
    private EntityMentionGraph latestGraph = EntityMentionGraph.empty();
    private boolean updatingGraphFilters;

    private final TextField searchField;

    private final Grid<PropositionRow> propositionGrid;
    private final Grid<UnitRow> unitGrid;

    public KnowledgeBrowserPanel(ArcMemEngine arcMemEngine, MemoryUnitRepository contextUnitRepository) {
        this.arcMemEngine = arcMemEngine;
        this.contextUnitRepository = contextUnitRepository;
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        searchField = new TextField();
        searchField.setPlaceholder("Semantic search (min 3 chars)...");
        searchField.setWidthFull();
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(500);
        searchField.addValueChangeListener(e -> onSearchChanged(e.getValue()));

        searchResultsContent = new VerticalLayout();
        searchResultsContent.setPadding(true);
        searchResultsContent.setSpacing(true);
        searchResultsContent.setVisible(false);
        searchResultsContent.addClassName("ar-scrollable");

        propositionsTab = new Tab("Propositions");
        unitsTab = new Tab("Memory Units");
        graphTab = new Tab("Graph");
        tabs = new Tabs(propositionsTab, unitsTab, graphTab);
        tabs.setWidthFull();

        statusFilter = new Select<>();
        statusFilter.setLabel("Status");
        statusFilter.setItems("ALL", "ACTIVE", "SUPERSEDED", "CONTRADICTED", "PROMOTED");
        statusFilter.setValue("ALL");
        statusFilter.setWidth("140px");
        statusFilter.addValueChangeListener(e -> refreshPropositions());

        minConfidenceFilter = new NumberField("Min Confidence");
        minConfidenceFilter.setMin(0.0);
        minConfidenceFilter.setMax(1.0);
        minConfidenceFilter.setStep(0.1);
        minConfidenceFilter.setValue(0.0);
        minConfidenceFilter.setWidth("140px");
        minConfidenceFilter.addValueChangeListener(e -> refreshPropositions());

        var propositionFilters = new HorizontalLayout(statusFilter, minConfidenceFilter);
        propositionFilters.setSpacing(true);
        propositionFilters.setAlignItems(Alignment.BASELINE);

        propositionGrid = new Grid<>();
        propositionGrid.addColumn(PropositionRow::text).setHeader("Text").setFlexGrow(3).setAutoWidth(false);
        propositionGrid.addColumn(PropositionRow::status).setHeader("Status").setWidth("100px");
        propositionGrid.addColumn(PropositionRow::confidence).setHeader("Confidence").setWidth("100px");
        propositionGrid.addColumn(PropositionRow::created).setHeader("Created").setWidth("140px");
        propositionGrid.setSizeFull();

        propositionsContent = new VerticalLayout(propositionFilters, propositionGrid);
        propositionsContent.setPadding(true);
        propositionsContent.setSpacing(true);
        propositionsContent.setSizeFull();
        propositionsContent.setFlexGrow(1, propositionGrid);
        propositionsContent.addClassName("ar-scrollable");

        authorityFilter = new Select<>();
        authorityFilter.setLabel("Authority");
        authorityFilter.setItems("ALL", "PROVISIONAL", "UNRELIABLE", "RELIABLE", "CANON");
        authorityFilter.setValue("ALL");
        authorityFilter.setWidth("140px");
        authorityFilter.addValueChangeListener(e -> refreshUnits());

        minRankFilter = new NumberField("Min Rank");
        minRankFilter.setMin(100);
        minRankFilter.setMax(900);
        minRankFilter.setStep(50);
        minRankFilter.setValue(100.0);
        minRankFilter.setWidth("120px");
        minRankFilter.addValueChangeListener(e -> refreshUnits());

        maxRankFilter = new NumberField("Max Rank");
        maxRankFilter.setMin(100);
        maxRankFilter.setMax(900);
        maxRankFilter.setStep(50);
        maxRankFilter.setValue(900.0);
        maxRankFilter.setWidth("120px");
        maxRankFilter.addValueChangeListener(e -> refreshUnits());

        var unitFilters = new HorizontalLayout(authorityFilter, minRankFilter, maxRankFilter);
        unitFilters.setSpacing(true);
        unitFilters.setAlignItems(Alignment.BASELINE);

        unitGrid = new Grid<>();
        unitGrid.addColumn(UnitRow::text).setHeader("Text").setFlexGrow(3).setAutoWidth(false);
        unitGrid.addColumn(UnitRow::rank).setHeader("Rank").setWidth("80px").setSortable(true);
        unitGrid.addColumn(UnitRow::authority).setHeader("Authority").setWidth("110px").setSortable(true);
        unitGrid.addColumn(UnitRow::pinned).setHeader("Pinned").setWidth("80px");
        unitGrid.addColumn(UnitRow::reinforcementCount).setHeader("Reinforced").setWidth("100px");
        unitGrid.setSizeFull();

        unitsContent = new VerticalLayout(unitFilters, unitGrid);
        unitsContent.setPadding(true);
        unitsContent.setSpacing(true);
        unitsContent.setSizeFull();
        unitsContent.setFlexGrow(1, unitGrid);
        unitsContent.addClassName("ar-scrollable");
        unitsContent.setVisible(false);

        minEdgeWeightFilter = new NumberField("Min Edge Weight");
        minEdgeWeightFilter.setMin(1);
        minEdgeWeightFilter.setMax(20);
        minEdgeWeightFilter.setStep(1);
        minEdgeWeightFilter.setValue(2.0);
        minEdgeWeightFilter.setWidth("160px");
        minEdgeWeightFilter.addValueChangeListener(e -> refreshGraph());

        entityTypeFilter = new Select<>();
        entityTypeFilter.setLabel("Entity Type");
        entityTypeFilter.setItems("ALL");
        entityTypeFilter.setValue("ALL");
        entityTypeFilter.setWidth("170px");
        entityTypeFilter.addValueChangeListener(e -> {
            if (!updatingGraphFilters) {
                refreshGraph();
            }
        });

        graphScopeFilter = new Select<>();
        graphScopeFilter.setLabel("Scope");
        graphScopeFilter.setItems("ALL_PROPOSITIONS", "ACTIVE_ONLY");
        graphScopeFilter.setItemLabelGenerator(value -> "ACTIVE_ONLY".equals(value) ? "Active Only" : "All Propositions");
        graphScopeFilter.setValue("ACTIVE_ONLY");
        graphScopeFilter.setWidth("180px");
        graphScopeFilter.addValueChangeListener(e -> refreshGraph());

        entityMentionNetworkView = new EntityMentionNetworkView();

        resetGraphLayoutButton = new Button("Reset Layout");
        resetGraphLayoutButton.addClickListener(e -> entityMentionNetworkView.resetLayout());

        var graphFilters = new HorizontalLayout(minEdgeWeightFilter, entityTypeFilter, graphScopeFilter, resetGraphLayoutButton);
        graphFilters.setSpacing(true);
        graphFilters.setAlignItems(Alignment.BASELINE);

        graphContent = new VerticalLayout(graphFilters, entityMentionNetworkView);
        graphContent.setPadding(true);
        graphContent.setSpacing(true);
        graphContent.setSizeFull();
        graphContent.setFlexGrow(1, entityMentionNetworkView);
        graphContent.addClassName("ar-graph-content");
        graphContent.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            propositionsContent.setVisible(selected == propositionsTab);
            unitsContent.setVisible(selected == unitsTab);
            graphContent.setVisible(selected == graphTab);
            if (selected == graphTab && contextId != null) {
                refreshGraph();
            }
        });

        add(searchField, searchResultsContent, tabs, propositionsContent, unitsContent, graphContent);
        setFlexGrow(1, propositionsContent);
        setFlexGrow(1, unitsContent);
        setFlexGrow(1, graphContent);

        showEmptyState();
    }

    @Override
    public void onTurnCompleted(SimulationProgress progress) {
        if (progress.contextTrace() != null) {
            refresh();
        }
    }

    /**
     * Set the simulation context ID and refresh all data.
     * All browsing data is scoped to this context.
     */
    public void setContextId(@Nullable String contextId) {
        this.contextId = contextId;
        refresh();
    }

    public @Nullable String getContextId() {
        return contextId;
    }

    /**
     * Refresh all tabs with current contextId data.
     */
    public void refresh() {
        refreshPropositions();
        refreshUnits();
        refreshGraph();
        clearSearchResults();
    }

    /**
     * Set the search field to filter for a specific unit text,
     * triggering a search. Used for cross-panel linking from
     * ContextInspectorPanel "Browse" actions.
     */
    public void filterByUnitText(String unitText) {
        searchField.setValue(unitText);
    }

    /**
     * Focus the graph tab to highlight a likely entity neighborhood.
     */
    public void focusGraphForUnitText(String unitText) {
        selectGraphTab();
        if (contextId == null) {
            return;
        }
        if (latestGraph.nodes().isEmpty()) {
            refreshGraph();
        }
        if (entityMentionNetworkView.focusEntity(unitText)) {
            return;
        }
        var tokens = unitText.split("\\W+");
        for (var token : tokens) {
            if (token.length() < 4) {
                continue;
            }
            if (entityMentionNetworkView.focusEntity(token)) {
                return;
            }
        }
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        this.contextId = null;
        propositionGrid.setItems(List.of());
        unitGrid.setItems(List.of());
        latestGraph = EntityMentionGraph.empty();
        entityMentionNetworkView.setGraph(latestGraph);
        clearSearchResults();
        showEmptyState();
    }

    private void refreshPropositions() {
        if (contextId == null) {
            propositionGrid.setItems(List.of());
            return;
        }
        try {
            var propositions = contextUnitRepository.findByContextIdValue(contextId);
            var statusValue = statusFilter.getValue();
            var minConf = minConfidenceFilter.getValue() != null ? minConfidenceFilter.getValue() : 0.0;
            var rows = propositions.stream()
                                   .filter(p -> {
                                       var propositionStatus = p.getStatus() != null ? p.getStatus().name() : "ACTIVE";
                                       return "ALL".equals(statusValue) || propositionStatus.equals(statusValue);
                                   })
                                   .filter(p -> p.getConfidence() >= minConf)
                                   .map(KnowledgeBrowserPanel::toPropositionRow)
                                   .toList();
            propositionGrid.setItems(rows);
        } catch (Exception e) {
            logger.warn("Failed to load propositions for context {}: {}", contextId, e.getMessage());
            propositionGrid.setItems(List.of());
        }
    }

    private void refreshUnits() {
        if (contextId == null) {
            unitGrid.setItems(List.of());
            return;
        }
        try {
            var units = arcMemEngine.inject(contextId);
            var authorityValue = authorityFilter.getValue();
            var minRank = minRankFilter.getValue() != null ? minRankFilter.getValue().intValue() : MemoryUnit.MIN_RANK;
            var maxRank = maxRankFilter.getValue() != null ? maxRankFilter.getValue().intValue() : MemoryUnit.MAX_RANK;
            var rows = units.stream()
                              .filter(a -> "ALL".equals(authorityValue) || a.authority().name().equals(authorityValue))
                              .filter(a -> a.rank() >= minRank && a.rank() <= maxRank)
                              .map(KnowledgeBrowserPanel::toUnitRow)
                              .toList();
            unitGrid.setItems(rows);
        } catch (Exception e) {
            logger.warn("Failed to load units for context {}: {}", contextId, e.getMessage());
            unitGrid.setItems(List.of());
        }
    }

    private void refreshGraph() {
        if (contextId == null) {
            latestGraph = EntityMentionGraph.empty();
            entityMentionNetworkView.setGraph(latestGraph);
            return;
        }

        try {
            var minEdgeWeight = minEdgeWeightFilter.getValue() != null ? minEdgeWeightFilter.getValue().intValue() : 1;
            var activeOnly = "ACTIVE_ONLY".equals(graphScopeFilter.getValue());
            var typeValue = entityTypeFilter.getValue();
            var filter = new EntityMentionGraphFilter(minEdgeWeight, normalizeEntityType(typeValue), activeOnly);
            latestGraph = contextUnitRepository.findEntityMentionGraph(contextId, filter);

            updateEntityTypeFilterOptions(typeValue);
            entityMentionNetworkView.setGraph(latestGraph);
        } catch (Exception e) {
            logger.warn("Failed to load entity mention graph for context {}: {}", contextId, e.getMessage());
            latestGraph = EntityMentionGraph.empty();
            entityMentionNetworkView.setGraph(latestGraph);
        }
    }

    private void updateEntityTypeFilterOptions(@Nullable String selected) {
        var options = latestGraph.nodes().stream()
                                 .map(node -> node.type().toUpperCase(Locale.ROOT))
                                 .distinct()
                                 .sorted(Comparator.naturalOrder())
                                 .toList();
        var allOptions = new ArrayList<String>();
        allOptions.add("ALL");
        allOptions.addAll(options);

        updatingGraphFilters = true;
        entityTypeFilter.setItems(allOptions);
        var targetValue = selected != null && allOptions.contains(selected) ? selected : "ALL";
        entityTypeFilter.setValue(targetValue);
        updatingGraphFilters = false;
    }

    private @Nullable String normalizeEntityType(@Nullable String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private void selectGraphTab() {
        tabs.setSelectedTab(graphTab);
        propositionsContent.setVisible(false);
        unitsContent.setVisible(false);
        graphContent.setVisible(true);
    }

    private void onSearchChanged(String query) {
        if (query == null || query.length() < SEARCH_MIN_LENGTH) {
            clearSearchResults();
            return;
        }
        if (contextId == null) {
            showSearchMessage("Set a context ID before searching.");
            return;
        }
        try {
            var results = contextUnitRepository.semanticSearch(
                    query, contextId, SEARCH_TOP_K, SEARCH_SIMILARITY_THRESHOLD);
            renderSearchResults(results);
        } catch (Exception e) {
            logger.warn("Semantic search failed for query '{}': {}", query, e.getMessage());
            showSearchMessage("Search failed: " + e.getMessage());
        }
    }

    private void renderSearchResults(List<MemoryUnitRepository.ScoredProposition> results) {
        searchResultsContent.removeAll();
        searchResultsContent.setVisible(true);
        if (results.isEmpty()) {
            showSearchMessage("No matching propositions found.");
            return;
        }
        var header = new Span("%d results".formatted(results.size()));
        header.addClassName("ar-search-header");
        searchResultsContent.add(header);
        for (var result : results) {
            var row = new HorizontalLayout();
            row.setWidthFull();
            row.setSpacing(true);
            row.addClassName("ar-search-row");
            var scoreBadge = new Span("%.0f%%".formatted(result.score() * 100));
            scoreBadge.addClassName("ar-search-score");
            var text = new Span(result.text());
            text.addClassName("ar-search-text");
            row.add(scoreBadge, text);
            searchResultsContent.add(row);
        }
    }

    private void clearSearchResults() {
        searchResultsContent.removeAll();
        searchResultsContent.setVisible(false);
    }

    private void showSearchMessage(String message) {
        searchResultsContent.removeAll();
        searchResultsContent.setVisible(true);
        var msg = new Paragraph(message);
        msg.addClassName("ar-empty-message");
        searchResultsContent.add(msg);
    }

    private void showEmptyState() {
        propositionGrid.setItems(List.of());
        unitGrid.setItems(List.of());
        entityMentionNetworkView.setGraph(EntityMentionGraph.empty());
    }

    record PropositionRow(String id, String text, String status, double confidence, String created) {}

    record UnitRow(String id, String text, int rank, String authority, boolean pinned,
                     int reinforcementCount) {}

    private static PropositionRow toPropositionRow(Proposition p) {
        var status = p.getStatus() != null ? p.getStatus().name() : "ACTIVE";
        return new PropositionRow(
                p.getId(),
                p.getText(),
                status,
                p.getConfidence(),
                p.getCreated() != null ? p.getCreated().toString() : ""
        );
    }

    private static UnitRow toUnitRow(MemoryUnit a) {
        return new UnitRow(
                a.id(),
                a.text(),
                a.rank(),
                a.authority().name(),
                a.pinned(),
                a.reinforcementCount()
        );
    }
}
