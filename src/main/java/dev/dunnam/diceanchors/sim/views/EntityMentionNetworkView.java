package dev.dunnam.diceanchors.sim.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.persistence.EntityMentionEdge;
import dev.dunnam.diceanchors.persistence.EntityMentionGraph;
import dev.dunnam.diceanchors.persistence.EntityMentionNode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders an interactive entity co-mention network using Cytoscape.js.
 *
 * <p>Cytoscape is loaded lazily from CDN on first render. The graph uses a
 * force-directed (COSE) layout with node sizes mapped to mention count and
 * edge widths mapped to co-mention weight. Clicking a node highlights its
 * neighborhood; clicking again deselects.</p>
 */
public class EntityMentionNetworkView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(EntityMentionNetworkView.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CYTOSCAPE_CDN = "https://unpkg.com/cytoscape@3.30.4/dist/cytoscape.min.js";

    /**
     * Main render script. Lazy-loads Cytoscape from CDN, destroys any previous
     * instance, creates the graph with COSE layout, wires click handlers, and
     * sets up a MutationObserver for live theme switching.
     *
     * <p>Parameters passed via executeJs: $0=container element, $1=elements JSON,
     * $2=selected entity ID (or empty string), $3=server reference for callbacks.</p>
     */
    private static final String CYTOSCAPE_RENDER_JS = """
            (async function(container, elementsJson, selectedId, serverRef) {
                // Lazy-load Cytoscape
                if (!window.cytoscape) {
                    await new Promise((resolve, reject) => {
                        var s = document.createElement('script');
                        s.src = '%s';
                        s.onload = resolve;
                        s.onerror = reject;
                        document.head.appendChild(s);
                    });
                }

                // Destroy previous instance
                if (container._cy) {
                    container._cy.destroy();
                    container._cy = null;
                }
                if (container._themeObs) {
                    container._themeObs.disconnect();
                    container._themeObs = null;
                }

                var elements = JSON.parse(elementsJson);
                if (!elements || elements.length === 0) return;

                // Detect dark/light theme
                function isDark() {
                    var t = document.documentElement.getAttribute('theme') || '';
                    return !t.includes('light');
                }

                function themeColors() {
                    var dark = isDark();
                    return {
                        nodeBg:       dark ? '#1a1a24' : '#ede8df',
                        nodeText:     dark ? '#e0e0e0' : '#1a1a2e',
                        nodeBorder:   dark ? '#2a2a3a' : '#c8c0b0',
                        edgeColor:    dark ? '#6b7280' : '#9ca3af',
                        edgeLabel:    dark ? '#9090a0' : '#4a4a6a',
                        selectedBorder: dark ? '#00d4ff' : '#0097a7',
                        fadedOpacity: 0.15
                    };
                }

                var tc = themeColors();

                var cy = window.cytoscape({
                    container: container,
                    elements: elements,
                    layout: {
                        name: 'cose',
                        animate: true,
                        animationDuration: 400,
                        nodeRepulsion: function() { return 8000; },
                        idealEdgeLength: function() { return 120; },
                        gravity: 0.3,
                        padding: 30
                    },
                    style: [
                        {
                            selector: 'node',
                            style: {
                                'label': 'data(label)',
                                'text-valign': 'center',
                                'text-halign': 'center',
                                'font-size': '11px',
                                'font-family': '"JetBrains Mono", "Fira Code", monospace',
                                'width': 'data(size)',
                                'height': 'data(size)',
                                'background-color': tc.nodeBg,
                                'border-width': 1,
                                'border-color': tc.nodeBorder,
                                'color': tc.nodeText,
                                'text-max-width': '100px',
                                'text-wrap': 'ellipsis',
                                'text-overflow-wrap': 'anywhere',
                                'min-zoomed-font-size': 8
                            }
                        },
                        {
                            selector: 'edge',
                            style: {
                                'width': 'data(edgeWidth)',
                                'line-color': tc.edgeColor,
                                'curve-style': 'bezier',
                                'opacity': 0.72,
                                'label': 'data(weightLabel)',
                                'font-size': '10px',
                                'font-family': '"JetBrains Mono", "Fira Code", monospace',
                                'color': tc.edgeLabel,
                                'text-background-color': tc.nodeBg,
                                'text-background-opacity': 0.7,
                                'text-background-padding': '2px',
                                'text-rotation': 'autorotate'
                            }
                        },
                        {
                            selector: 'node.highlighted',
                            style: {
                                'border-width': 2,
                                'border-color': tc.selectedBorder,
                                'opacity': 1
                            }
                        },
                        {
                            selector: 'node.faded',
                            style: {
                                'opacity': tc.fadedOpacity
                            }
                        },
                        {
                            selector: 'edge.highlighted',
                            style: {
                                'opacity': 0.95,
                                'line-color': tc.selectedBorder
                            }
                        },
                        {
                            selector: 'edge.faded',
                            style: {
                                'opacity': tc.fadedOpacity
                            }
                        }
                    ],
                    minZoom: 0.3,
                    maxZoom: 3,
                    wheelSensitivity: 0.3
                });

                container._cy = cy;
                container._serverRef = serverRef;

                // Click handler
                cy.on('tap', 'node', function(evt) {
                    var nodeId = evt.target.id();
                    if (container._serverRef) {
                        container._serverRef.$server.onNodeClicked(nodeId);
                    }
                });

                // Background click clears selection
                cy.on('tap', function(evt) {
                    if (evt.target === cy) {
                        if (container._serverRef) {
                            container._serverRef.$server.onNodeClicked('');
                        }
                    }
                });

                // Apply initial highlight if selectedId is set
                if (selectedId) {
                    %s
                }

                // Theme observer for live dark/light switching
                var observer = new MutationObserver(function() {
                    var ntc = themeColors();
                    cy.style()
                      .selector('node').style({
                          'background-color': ntc.nodeBg,
                          'border-color': ntc.nodeBorder,
                          'color': ntc.nodeText
                      })
                      .selector('edge').style({
                          'line-color': ntc.edgeColor,
                          'color': ntc.edgeLabel,
                          'text-background-color': ntc.nodeBg
                      })
                      .selector('node.highlighted').style({
                          'border-color': ntc.selectedBorder
                      })
                      .selector('edge.highlighted').style({
                          'line-color': ntc.selectedBorder
                      })
                      .update();
                });
                observer.observe(document.documentElement, { attributes: true, attributeFilter: ['theme'] });
                container._themeObs = observer;
            })($0, $1, $2, $3)
            """.formatted(CYTOSCAPE_CDN, highlightSnippet("selectedId"));

    /**
     * Lightweight highlight-only JS that updates node/edge classes without
     * re-layout. Parameters: $0=container, $1=selected entity ID (or empty).
     */
    private static final String HIGHLIGHT_JS = """
            (function(container, selectedId) {
                var cy = container._cy;
                if (!cy) return;
                %s
            })($0, $1)
            """.formatted(highlightSnippet("selectedId"));

    /**
     * Re-layout JS. Parameters: $0=container.
     */
    private static final String RELAYOUT_JS = """
            (function(container) {
                var cy = container._cy;
                if (!cy) return;
                cy.layout({
                    name: 'cose',
                    animate: true,
                    animationDuration: 400,
                    nodeRepulsion: function() { return 8000; },
                    idealEdgeLength: function() { return 120; },
                    gravity: 0.3,
                    padding: 30
                }).run();
            })($0)
            """;

    private static String highlightSnippet(String varName) {
        return """
                cy.elements().removeClass('highlighted faded');
                if (%s) {
                    var sel = cy.getElementById(%s);
                    if (sel.length > 0) {
                        var neighborhood = sel.neighborhood().add(sel);
                        neighborhood.addClass('highlighted');
                        cy.elements().not(neighborhood).addClass('faded');
                    }
                }
                """.formatted(varName, varName);
    }

    private final Paragraph stateMessage;
    private final Span summaryLabel;
    private final Div canvas;

    private EntityMentionGraph graph = EntityMentionGraph.empty();
    private @Nullable String selectedEntityId;
    private int maxVisibleNodes = 45;
    private int maxVisibleEdges = 120;

    public EntityMentionNetworkView() {
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        summaryLabel = new Span();
        summaryLabel.addClassName("ar-status-label");

        stateMessage = new Paragraph();
        stateMessage.addClassName("ar-graph-state-message");

        canvas = new Div();
        canvas.setWidthFull();
        canvas.addClassName("ar-graph-canvas");

        add(summaryLabel, stateMessage, canvas);
        setFlexGrow(1, canvas);
        setGraph(EntityMentionGraph.empty());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        canvas.getElement().executeJs(
                "(function(c){ if(c._cy){c._cy.destroy();c._cy=null;} if(c._themeObs){c._themeObs.disconnect();c._themeObs=null;} })($0)",
                canvas.getElement());
    }

    public void setMaxVisible(int maxNodes, int maxEdges) {
        maxVisibleNodes = Math.max(5, maxNodes);
        maxVisibleEdges = Math.max(5, maxEdges);
        render();
    }

    public void setGraph(EntityMentionGraph graph) {
        this.graph = graph != null ? graph : EntityMentionGraph.empty();
        selectedEntityId = null;
        render();
    }

    public void resetLayout() {
        selectedEntityId = null;
        render();
    }

    public boolean focusEntity(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        var normalized = query.trim().toLowerCase(Locale.ROOT);
        var match = graph.nodes().stream()
                         .filter(node -> node.entityId().equalsIgnoreCase(query))
                         .findFirst()
                         .or(() -> graph.nodes().stream()
                                        .filter(node -> node.label().toLowerCase(Locale.ROOT).contains(normalized))
                                        .findFirst());
        if (match.isEmpty()) {
            return false;
        }
        selectedEntityId = match.get().entityId();
        updateHighlight();
        return true;
    }

    public void focusEntityId(@Nullable String entityId) {
        selectedEntityId = entityId;
        updateHighlight();
    }

    @Nullable
    public String selectedEntityId() {
        return selectedEntityId;
    }

    int renderedNodeCount() {
        return Math.min(graph.nodes().size(), maxVisibleNodes);
    }

    int renderedEdgeCount() {
        return Math.min(graph.edges().size(), maxVisibleEdges);
    }

    /**
     * Called from client-side click handler.
     */
    @ClientCallable
    public void onNodeClicked(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            selectedEntityId = null;
        } else {
            selectedEntityId = nodeId.equals(selectedEntityId) ? null : nodeId;
        }
        updateHighlight();
    }

    private void updateHighlight() {
        var sel = selectedEntityId != null ? selectedEntityId : "";
        canvas.getElement().executeJs(HIGHLIGHT_JS, canvas.getElement(), sel);
    }

    private void render() {
        if (graph.nodes().isEmpty()) {
            summaryLabel.setText("0 entities, 0 edges");
            stateMessage.setText("No entity mention network data is available for this context.");
            stateMessage.setVisible(true);
            canvas.getElement().executeJs(
                    "(function(c){ if(c._cy){c._cy.destroy();c._cy=null;} })($0)",
                    canvas.getElement());
            return;
        }

        var sortedNodes = graph.nodes().stream()
                               .sorted(Comparator.comparing(EntityMentionNode::entityId))
                               .limit(maxVisibleNodes)
                               .toList();
        var nodeIds = sortedNodes.stream().map(EntityMentionNode::entityId).collect(Collectors.toSet());

        var visibleEdges = graph.edges().stream()
                                .filter(edge -> nodeIds.contains(edge.sourceEntityId()) && nodeIds.contains(edge.targetEntityId()))
                                .sorted(Comparator.comparingInt(EntityMentionEdge::weight).reversed()
                                                  .thenComparing(EntityMentionEdge::sourceEntityId)
                                                  .thenComparing(EntityMentionEdge::targetEntityId))
                                .limit(maxVisibleEdges)
                                .toList();

        if (visibleEdges.isEmpty()) {
            summaryLabel.setText("%d entities, 0 edges".formatted(sortedNodes.size()));
            stateMessage.setText("No co-mention edges match the current filters.");
            stateMessage.setVisible(true);
            canvas.getElement().executeJs(
                    "(function(c){ if(c._cy){c._cy.destroy();c._cy=null;} })($0)",
                    canvas.getElement());
            return;
        }

        var denseWarning = graph.nodes().size() > sortedNodes.size() || graph.edges().size() > visibleEdges.size();
        summaryLabel.setText("%d entities, %d edges%s".formatted(
                sortedNodes.size(),
                visibleEdges.size(),
                denseWarning ? " (truncated for readability)" : ""));
        stateMessage.setVisible(false);

        var elementsJson = toElementsJson(sortedNodes, visibleEdges);
        var sel = selectedEntityId != null ? selectedEntityId : "";

        canvas.getElement().executeJs(CYTOSCAPE_RENDER_JS,
                canvas.getElement(), elementsJson, sel, getElement());
    }

    private String toElementsJson(List<EntityMentionNode> nodes, List<EntityMentionEdge> edges) {
        var elements = new ArrayList<Map<String, Object>>();

        int maxMentions = nodes.stream().mapToInt(EntityMentionNode::mentionCount).max().orElse(1);

        for (var node : nodes) {
            var label = node.label();
            if (label.length() > 18) {
                label = label.substring(0, 18) + "...";
            }
            int size = 30 + (int) (40.0 * node.mentionCount() / Math.max(1, maxMentions));

            var data = new HashMap<String, Object>();
            data.put("id", node.entityId());
            data.put("label", label);
            data.put("size", size);
            data.put("fullLabel", node.label());
            data.put("type", node.type());
            data.put("mentionCount", node.mentionCount());
            data.put("propositionCount", node.propositionCount());

            elements.add(Map.of("data", data));
        }

        for (var edge : edges) {
            var edgeWidth = 1.4 + Math.log(Math.max(1, edge.weight()));

            var data = new HashMap<String, Object>();
            data.put("id", edge.sourceEntityId() + "-" + edge.targetEntityId());
            data.put("source", edge.sourceEntityId());
            data.put("target", edge.targetEntityId());
            data.put("weight", edge.weight());
            data.put("weightLabel", String.valueOf(edge.weight()));
            data.put("edgeWidth", Math.round(edgeWidth * 100.0) / 100.0);
            data.put("propositionIds", edge.propositionIds());

            elements.add(Map.of("data", data));
        }

        try {
            return MAPPER.writeValueAsString(elements);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize graph elements to JSON", e);
            return "[]";
        }
    }
}
