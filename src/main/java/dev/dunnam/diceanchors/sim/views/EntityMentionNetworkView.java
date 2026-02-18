package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.persistence.EntityMentionEdge;
import dev.dunnam.diceanchors.persistence.EntityMentionGraph;
import dev.dunnam.diceanchors.persistence.EntityMentionNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders an interactive entity co-mention network with deterministic layout.
 */
public class EntityMentionNetworkView extends VerticalLayout {

    private static final int VIEWPORT_WIDTH = 960;
    private static final int VIEWPORT_HEIGHT = 520;

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
        summaryLabel.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");

        stateMessage = new Paragraph();
        stateMessage.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("text-align", "center")
                    .set("margin", "0")
                    .set("padding", "6px 0");

        canvas = new Div();
        canvas.setWidthFull();
        canvas.getStyle()
              .set("position", "relative")
              .set("min-height", "460px")
              .set("border", "1px solid var(--lumo-contrast-10pct)")
              .set("border-radius", "var(--lumo-border-radius-m)")
              .set("background", "linear-gradient(180deg, var(--lumo-base-color), var(--lumo-contrast-5pct))")
              .set("overflow", "hidden");

        add(summaryLabel, stateMessage, canvas);
        setFlexGrow(1, canvas);
        setGraph(EntityMentionGraph.empty());
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
        render();
        return true;
    }

    public void focusEntityId(@Nullable String entityId) {
        selectedEntityId = entityId;
        render();
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

    private void render() {
        canvas.getElement().removeAllChildren();

        if (graph.nodes().isEmpty()) {
            summaryLabel.setText("0 entities, 0 edges");
            stateMessage.setText("No entity mention network data is available for this context.");
            stateMessage.setVisible(true);
            return;
        }

        var sortedNodes = graph.nodes().stream()
                               .sorted(Comparator.comparing(EntityMentionNode::entityId))
                               .limit(maxVisibleNodes)
                               .toList();
        var nodeIds = sortedNodes.stream().map(EntityMentionNode::entityId).collect(java.util.stream.Collectors.toSet());

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
            return;
        }

        var denseWarning = graph.nodes().size() > sortedNodes.size() || graph.edges().size() > visibleEdges.size();
        summaryLabel.setText("%d entities, %d edges%s".formatted(
                sortedNodes.size(),
                visibleEdges.size(),
                denseWarning ? " (truncated for readability)" : ""));
        stateMessage.setVisible(false);

        var positions = layout(sortedNodes);
        var neighbors = buildNeighborMap(visibleEdges);

        var svg = new com.vaadin.flow.dom.Element("svg");
        svg.setAttribute("viewBox", "0 0 %d %d".formatted(VIEWPORT_WIDTH, VIEWPORT_HEIGHT));
        svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
        svg.getStyle().set("position", "absolute");
        svg.getStyle().set("inset", "0");
        svg.getStyle().set("width", "100%");
        svg.getStyle().set("height", "100%");

        var highlightedNodes = highlightedNodes(neighbors);
        for (var edge : visibleEdges) {
            var source = positions.get(edge.sourceEntityId());
            var target = positions.get(edge.targetEntityId());
            if (source == null || target == null) {
                continue;
            }
            var edgeElement = new com.vaadin.flow.dom.Element("line");
            edgeElement.setAttribute("x1", format(source.x));
            edgeElement.setAttribute("y1", format(source.y));
            edgeElement.setAttribute("x2", format(target.x));
            edgeElement.setAttribute("y2", format(target.y));
            edgeElement.setAttribute("stroke", "#6b7280");
            edgeElement.setAttribute("stroke-width", format(1.4 + Math.log(Math.max(1, edge.weight()))));
            edgeElement.setAttribute("opacity", edgeOpacity(edge, highlightedNodes));

            var title = new com.vaadin.flow.dom.Element("title");
            title.setText("weight=%d, propositions=%s".formatted(edge.weight(), String.join(", ", edge.propositionIds())));
            edgeElement.appendChild(title);
            svg.appendChild(edgeElement);

            var midpointX = (source.x + target.x) / 2.0;
            var midpointY = (source.y + target.y) / 2.0;
            var label = new com.vaadin.flow.dom.Element("text");
            label.setAttribute("x", format(midpointX));
            label.setAttribute("y", format(midpointY));
            label.setAttribute("fill", "#334155");
            label.setAttribute("font-size", "11");
            label.setAttribute("text-anchor", "middle");
            label.setAttribute("dominant-baseline", "middle");
            label.setAttribute("opacity", edgeOpacity(edge, highlightedNodes));
            label.setText(String.valueOf(edge.weight()));
            svg.appendChild(label);
        }
        canvas.getElement().appendChild(svg);

        for (var node : sortedNodes) {
            var point = positions.get(node.entityId());
            if (point == null) {
                continue;
            }
            var nodeBadge = new Div();
            nodeBadge.setText(nodeLabel(node));
            nodeBadge.getStyle()
                     .set("position", "absolute")
                     .set("left", "calc(%s%% - 58px)".formatted(format(point.x / VIEWPORT_WIDTH * 100.0)))
                     .set("top", "calc(%s%% - 16px)".formatted(format(point.y / VIEWPORT_HEIGHT * 100.0)))
                     .set("width", "116px")
                     .set("height", "32px")
                     .set("border-radius", "16px")
                     .set("border", nodeBorder(node.entityId()))
                     .set("background", "rgba(255,255,255,0.92)")
                     .set("box-shadow", "0 1px 3px rgba(0,0,0,0.15)")
                     .set("display", "flex")
                     .set("align-items", "center")
                     .set("justify-content", "center")
                     .set("text-align", "center")
                     .set("font-size", "11px")
                     .set("padding", "0 8px")
                     .set("cursor", "pointer")
                     .set("opacity", nodeOpacity(node.entityId(), highlightedNodes));
            nodeBadge.getElement().setProperty("title", "%s (%s), mentions=%d, propositions=%d"
                    .formatted(node.label(), node.type(), node.mentionCount(), node.propositionCount()));
            nodeBadge.addClickListener(event -> {
                selectedEntityId = node.entityId().equals(selectedEntityId) ? null : node.entityId();
                render();
            });
            canvas.add(nodeBadge);
        }
    }

    private Map<String, Point> layout(List<EntityMentionNode> sortedNodes) {
        var points = new HashMap<String, Point>();
        var count = sortedNodes.size();
        if (count == 1) {
            points.put(sortedNodes.getFirst().entityId(), new Point(VIEWPORT_WIDTH / 2.0, VIEWPORT_HEIGHT / 2.0));
            return points;
        }
        var radius = Math.min(VIEWPORT_WIDTH, VIEWPORT_HEIGHT) * 0.37;
        var centerX = VIEWPORT_WIDTH / 2.0;
        var centerY = VIEWPORT_HEIGHT / 2.0;
        for (int i = 0; i < count; i++) {
            var angle = -Math.PI / 2 + (2 * Math.PI * i / count);
            var x = centerX + radius * Math.cos(angle);
            var y = centerY + radius * Math.sin(angle);
            points.put(sortedNodes.get(i).entityId(), new Point(x, y));
        }
        return points;
    }

    private Map<String, Set<String>> buildNeighborMap(List<EntityMentionEdge> edges) {
        var neighbors = new HashMap<String, Set<String>>();
        for (var edge : edges) {
            neighbors.computeIfAbsent(edge.sourceEntityId(), ignored -> new HashSet<>()).add(edge.targetEntityId());
            neighbors.computeIfAbsent(edge.targetEntityId(), ignored -> new HashSet<>()).add(edge.sourceEntityId());
        }
        return neighbors;
    }

    private Set<String> highlightedNodes(Map<String, Set<String>> neighbors) {
        if (selectedEntityId == null) {
            return Set.of();
        }
        var highlighted = new HashSet<String>();
        highlighted.add(selectedEntityId);
        highlighted.addAll(neighbors.getOrDefault(selectedEntityId, Set.of()));
        return highlighted;
    }

    private String edgeOpacity(EntityMentionEdge edge, Set<String> highlightedNodes) {
        if (selectedEntityId == null) {
            return "0.72";
        }
        var connected = highlightedNodes.contains(edge.sourceEntityId()) && highlightedNodes.contains(edge.targetEntityId());
        return connected ? "0.95" : "0.15";
    }

    private String nodeOpacity(String nodeId, Set<String> highlightedNodes) {
        if (selectedEntityId == null) {
            return "1";
        }
        return highlightedNodes.contains(nodeId) ? "1" : "0.25";
    }

    private String nodeBorder(String nodeId) {
        if (selectedEntityId != null && selectedEntityId.equals(nodeId)) {
            return "2px solid var(--lumo-primary-color)";
        }
        return "1px solid var(--lumo-contrast-40pct)";
    }

    private String nodeLabel(EntityMentionNode node) {
        var label = node.label();
        return label.length() > 18 ? label.substring(0, 18) + "..." : label;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Point(double x, double y) {}
}
