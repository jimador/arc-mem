package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.TrustScore;
import dev.dunnam.diceanchors.assembly.CompactionLossEvent;
import dev.dunnam.diceanchors.sim.engine.ContextTrace;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Panel showing injected anchors, drift evaluation verdicts, and compaction info
 * for the current turn.
 * <p>
 * Three tabs:
 * <ul>
 *   <li><b>Anchors</b> -- active anchor list with rank bars, authority badges, and trust scores</li>
 *   <li><b>Verdicts</b> -- drift evaluation results color-coded by verdict type</li>
 *   <li><b>Compaction</b> -- compaction trigger info, token savings, protected content</li>
 * </ul>
 */
public class ContextInspectorPanel extends VerticalLayout implements SimulationProgressListener {

    private static final int MAX_RANK = 900;

    private static final int PROMPT_TRUNCATE_LENGTH = 2000;

    private final VerticalLayout anchorsContent;
    private final VerticalLayout verdictsContent;
    private final VerticalLayout promptContent;
    private final VerticalLayout compactionContent;
    private final Span anchorCountBadge;
    private final Span verdictCountBadge;
    private final Span extractionCountBadge;

    private final Tab anchorsTab;
    private final Tab verdictsTab;
    private final Tab promptTab;
    private final Tab compactionTab;

    private @Nullable Consumer<String> browseCallback;
    private @Nullable Consumer<String> browseGraphCallback;

    public ContextInspectorPanel() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Tab headers
        anchorsTab = new Tab("Anchors");
        verdictsTab = new Tab("Verdicts");
        promptTab = new Tab("Prompt");
        compactionTab = new Tab("Compaction");
        var tabs = new Tabs(anchorsTab, verdictsTab, promptTab, compactionTab);
        tabs.setWidthFull();

        // Badge labels inside tab headers
        anchorCountBadge = tabBadge("0", "data-injection", "on");       // cyan / primary
        verdictCountBadge = tabBadge("0", "data-verdict", "contradicted"); // error (overridden per-render)
        extractionCountBadge = tabBadge("", "data-event-type", "extracted"); // teal
        extractionCountBadge.setVisible(false);
        anchorsTab.add(anchorCountBadge, extractionCountBadge);
        verdictsTab.add(verdictCountBadge);

        // Content panels
        anchorsContent = new VerticalLayout();
        anchorsContent.setPadding(true);
        anchorsContent.setSpacing(true);
        anchorsContent.setSizeFull();
        anchorsContent.addClassName("ar-scrollable");

        verdictsContent = new VerticalLayout();
        verdictsContent.setPadding(true);
        verdictsContent.setSpacing(true);
        verdictsContent.setSizeFull();
        verdictsContent.addClassName("ar-scrollable");
        verdictsContent.setVisible(false);

        promptContent = new VerticalLayout();
        promptContent.setPadding(true);
        promptContent.setSpacing(true);
        promptContent.setSizeFull();
        promptContent.addClassName("ar-scrollable");
        promptContent.setVisible(false);

        compactionContent = new VerticalLayout();
        compactionContent.setPadding(true);
        compactionContent.setSpacing(true);
        compactionContent.setSizeFull();
        compactionContent.addClassName("ar-scrollable");
        compactionContent.setVisible(false);

        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            anchorsContent.setVisible(selected == anchorsTab);
            verdictsContent.setVisible(selected == verdictsTab);
            promptContent.setVisible(selected == promptTab);
            compactionContent.setVisible(selected == compactionTab);
        });

        showPlaceholder(anchorsContent, "No anchors yet. Run a simulation to populate.");
        showPlaceholder(verdictsContent, "No verdicts yet. Verdicts appear on adversarial turns.");
        showPlaceholder(promptContent, "No prompt data yet. Select a turn to inspect.");
        showPlaceholder(compactionContent, "No compaction on this turn.");

        add(tabs, anchorsContent, verdictsContent, promptContent, compactionContent);
        setFlexGrow(1, anchorsContent);
        setFlexGrow(1, verdictsContent);
        setFlexGrow(1, promptContent);
        setFlexGrow(1, compactionContent);
    }

    @Override
    public void onTurnCompleted(SimulationProgress progress) {
        if (progress.contextTrace() != null) {
            List<EvalVerdict> verdicts = progress.verdicts() != null ? progress.verdicts() : List.of();
            update(progress.contextTrace(), verdicts);
        }
        if (progress.compactionResult() != null) {
            var cr = progress.compactionResult();
            updateCompaction(
                    cr.triggerReason(), cr.tokensBefore() - cr.tokensAfter(),
                    cr.protectedContentIds(), cr.summary(), cr.durationMs(), cr.lossEvents());
        }
    }

    /**
     * Update the panel with the latest context trace from a completed turn.
     */
    public void update(ContextTrace trace, List<EvalVerdict> verdicts) {
        anchorsContent.removeAll();
        verdictsContent.removeAll();

        if (trace == null || trace.injectedAnchors() == null || trace.injectedAnchors().isEmpty()) {
            showPlaceholder(anchorsContent, "No active anchors for this context.");
            anchorCountBadge.setText("0");
            extractionCountBadge.setVisible(false);
        } else {
            renderAnchors(trace);
        }

        if (verdicts == null || verdicts.isEmpty()) {
            showPlaceholder(verdictsContent, "No verdicts for this turn.");
            verdictCountBadge.setText("0");
        } else {
            renderVerdicts(verdicts);
        }

        // Prompt tab
        promptContent.removeAll();
        if (trace != null) {
            renderPrompt(trace);
        } else {
            showPlaceholder(promptContent, "No prompt data for this turn.");
        }

        // Compaction tab shows no-op by default; updateCompaction() overrides
        compactionContent.removeAll();
        showPlaceholder(compactionContent, "No compaction on this turn.");
    }

    /**
     * Update the compaction tab with compaction event data for the current turn.
     *
     * @param triggerReason  why compaction was triggered (e.g., "token threshold", "forced turn")
     * @param tokenSavings   tokens saved by compaction
     * @param protectedItems list of protected content descriptions
     * @param summaryPreview first 200 chars of the compaction summary
     * @param durationMs     compaction duration in milliseconds
     * @param lossEvents     anchors lost during compaction (may be null or empty)
     */
    public void updateCompaction(String triggerReason, int tokenSavings,
                                 List<String> protectedItems, String summaryPreview,
                                 long durationMs,
                                 @Nullable List<CompactionLossEvent> lossEvents) {
        compactionContent.removeAll();

        var reasonRow = metadataRow("Trigger", triggerReason);
        var savingsRow = metadataRow("Token Savings", String.valueOf(tokenSavings));
        var durationRow = metadataRow("Duration", "%d ms".formatted(durationMs));

        compactionContent.add(reasonRow, savingsRow, durationRow);

        // Lost facts section
        if (lossEvents != null && !lossEvents.isEmpty()) {
            var lossSection = new VerticalLayout();
            lossSection.setPadding(false);
            lossSection.setSpacing(false);

            var lossLabel = new Span("Lost Facts (%d)".formatted(lossEvents.size()));
            lossLabel.addClassName("ar-loss-label");
            lossSection.add(lossLabel);

            for (var loss : lossEvents) {
                var lossCard = new Div();
                lossCard.addClassName("ar-loss-card");

                var textSpan = new Span(loss.anchorText());
                textSpan.addClassName("ar-loss-text");

                var metaSpan = new Span("[%s] rank=%d".formatted(loss.authority().name(), loss.rank()));
                metaSpan.addClassName("ar-loss-meta");

                lossCard.add(textSpan, metaSpan);
                lossSection.add(lossCard);
            }
            compactionContent.add(lossSection);
        }

        // Protected content list
        if (protectedItems != null && !protectedItems.isEmpty()) {
            var protectedSection = new VerticalLayout();
            protectedSection.setPadding(false);
            protectedSection.setSpacing(false);

            var protectedLabel = new Span("Protected Content (%d)".formatted(protectedItems.size()));
            protectedLabel.addClassName("ar-protected-label");
            protectedSection.add(protectedLabel);

            for (var item : protectedItems) {
                var itemSpan = new Span(item);
                itemSpan.addClassName("ar-protected-item");
                protectedSection.add(itemSpan);
            }
            compactionContent.add(protectedSection);
        }

        // Summary preview
        if (summaryPreview != null && !summaryPreview.isBlank()) {
            var preview = truncate(summaryPreview, 200);
            var summaryLabel = new Span("Summary Preview");
            summaryLabel.addClassName("ar-compaction-summary-label");
            var summaryText = new Paragraph(preview);
            summaryText.addClassName("ar-compaction-summary-text");
            compactionContent.add(summaryLabel, summaryText);
        }
    }

    /**
     * Set a callback invoked when the user clicks "Browse" on an anchor card.
     * The callback receives the anchor text for cross-panel filtering.
     *
     * @param callback consumer that receives the anchor text
     */
    public void setBrowseCallback(@Nullable Consumer<String> callback) {
        this.browseCallback = callback;
    }

    /**
     * Set a callback invoked when the user clicks "Graph" on an anchor card.
     * The callback receives the anchor text for graph neighborhood focus.
     */
    public void setBrowseGraphCallback(@Nullable Consumer<String> callback) {
        this.browseGraphCallback = callback;
    }

    /**
     * Reset the panel to its initial empty state.
     */
    public void reset() {
        anchorsContent.removeAll();
        verdictsContent.removeAll();
        promptContent.removeAll();
        compactionContent.removeAll();
        showPlaceholder(anchorsContent, "No anchors yet. Run a simulation to populate.");
        showPlaceholder(verdictsContent, "No verdicts yet. Verdicts appear on adversarial turns.");
        showPlaceholder(promptContent, "No prompt data yet. Select a turn to inspect.");
        showPlaceholder(compactionContent, "No compaction on this turn.");
        anchorCountBadge.setText("0");
        verdictCountBadge.setText("0");
        extractionCountBadge.setVisible(false);
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    private void renderPrompt(ContextTrace trace) {
        if (trace.fullSystemPrompt() != null && !trace.fullSystemPrompt().isBlank()) {
            var systemLabel = new Span("System Prompt");
            systemLabel.addClassName("ar-prompt-label");
            promptContent.add(systemLabel);
            promptContent.add(promptBlock(trace.fullSystemPrompt()));
        }

        if (trace.fullUserPrompt() != null && !trace.fullUserPrompt().isBlank()) {
            var userLabel = new Span("User Prompt");
            userLabel.addClassName("ar-prompt-label");
            userLabel.addClassName("ar-prompt-label--user");
            promptContent.add(userLabel);
            promptContent.add(promptBlock(trace.fullUserPrompt()));
        }

        if ((trace.fullSystemPrompt() == null || trace.fullSystemPrompt().isBlank())
            && (trace.fullUserPrompt() == null || trace.fullUserPrompt().isBlank())) {
            showPlaceholder(promptContent, "No prompt data for this turn.");
        }
    }

    private Div promptBlock(String text) {
        var truncated = text.length() > PROMPT_TRUNCATE_LENGTH
                ? text.substring(0, PROMPT_TRUNCATE_LENGTH) + "\n\n... (truncated at %d chars)".formatted(PROMPT_TRUNCATE_LENGTH)
                : text;
        var block = new Div();
        block.setText(truncated);
        block.addClassName("ar-code-block");
        return block;
    }

    private void renderAnchors(ContextTrace trace) {
        var anchors = trace.injectedAnchors();
        anchorCountBadge.setText(String.valueOf(anchors.size()));

        // Extraction badge
        if (trace.propositionsExtracted() > 0) {
            extractionCountBadge.setText("%d extracted, %d promoted".formatted(
                    trace.propositionsExtracted(), trace.propositionsPromoted()));
            extractionCountBadge.setVisible(true);
        } else {
            extractionCountBadge.setVisible(false);
        }

        // Token summary
        var tokenSummaryText = "Token budget: %d injected-context tokens / %d total tokens"
                .formatted(trace.anchorTokens(), trace.totalTokens());
        if (trace.budgetApplied()) {
            tokenSummaryText += " (excluded anchors: %d)".formatted(trace.anchorsExcluded());
        }
        var tokenSummary = new Paragraph(tokenSummaryText);
        tokenSummary.addClassName("ar-token-summary");
        anchorsContent.add(tokenSummary);

        // Extraction summary when propositions were extracted this turn
        if (trace.propositionsExtracted() > 0) {
            var extractionSection = new VerticalLayout();
            extractionSection.setPadding(false);
            extractionSection.setSpacing(false);

            var extractionLabel = new Span("Extraction (%d extracted, %d promoted)".formatted(
                    trace.propositionsExtracted(), trace.propositionsPromoted()));
            extractionLabel.addClassName("ar-extraction-label");
            extractionSection.add(extractionLabel);

            for (var text : trace.extractedTexts()) {
                var textSpan = new Span(text);
                textSpan.addClassName("ar-extraction-item");
                extractionSection.add(textSpan);
            }
            anchorsContent.add(extractionSection);
        }

        for (var anchor : anchors) {
            anchorsContent.add(anchorCard(anchor));
        }
    }

    private Div anchorCard(Anchor anchor) {
        var card = new Div();
        card.addClassName("ar-anchor-card");

        // Header row: authority badge + anchor text
        var header = new HorizontalLayout();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.setSpacing(true);
        header.setWidthFull();

        var authorityBadge = authorityBadge(anchor.authority().name());
        var textSpan = new Span(anchor.text());

        if (anchor.pinned()) {
            var pinnedIcon = new Span("pinned");
            pinnedIcon.addClassName("ar-pinned-icon");
            header.add(authorityBadge, textSpan, pinnedIcon);
        } else {
            header.add(authorityBadge, textSpan);
        }
        header.expand(textSpan);

        if (browseCallback != null) {
            var browseButton = new Button("Browse");
            browseButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            browseButton.getStyle().set("margin-left", "auto");
            browseButton.addClickListener(e -> browseCallback.accept(anchor.text()));
            header.add(browseButton);
        }

        if (browseGraphCallback != null) {
            var graphButton = new Button("Graph");
            graphButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            graphButton.addClickListener(e -> browseGraphCallback.accept(anchor.text()));
            header.add(graphButton);
        }

        card.add(header);

        // Rank progress bar
        var rankRow = new HorizontalLayout();
        rankRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        rankRow.setSpacing(true);
        rankRow.setWidthFull();
        rankRow.addClassName("ar-rank-row");

        var rankLabel = new Span("Rank: " + anchor.rank());
        rankLabel.addClassName("ar-rank-label");

        var bar = new ProgressBar(0, MAX_RANK, anchor.rank());
        bar.setWidthFull();
        styleRankBar(bar, anchor.rank());

        var reinforceLabel = new Span("x" + anchor.reinforcementCount());
        reinforceLabel.addClassName("ar-reinforcement-label");

        rankRow.add(rankLabel, bar, reinforceLabel);
        card.add(rankRow);

        // Trust score display (9.5)
        if (anchor.trustScore() != null) {
            card.add(trustScoreSection(anchor.trustScore()));
        }

        return card;
    }

    /**
     * Render trust score section: composite score %, zone badge, and expandable signal audit.
     */
    private Div trustScoreSection(TrustScore trust) {
        var section = new Div();
        section.addClassName("ar-trust-section");

        // Score + zone row
        var scoreRow = new HorizontalLayout();
        scoreRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        scoreRow.setSpacing(true);

        var scoreLabel = new Span("Trust: %.0f%%".formatted(trust.score() * 100));
        scoreLabel.addClassName("ar-trust-score");
        scoreLabel.getElement().setAttribute("data-health", trustScoreHealth(trust.score()));

        var zoneBadge = promotionZoneBadge(trust.promotionZone().name());
        scoreRow.add(scoreLabel, zoneBadge);
        section.add(scoreRow);

        // Expandable signal audit
        if (trust.signalAudit() != null && !trust.signalAudit().isEmpty()) {
            var auditContent = new VerticalLayout();
            auditContent.setPadding(false);
            auditContent.setSpacing(false);

            for (var entry : trust.signalAudit().entrySet()) {
                var signalRow = new HorizontalLayout();
                signalRow.setSpacing(true);
                signalRow.setAlignItems(HorizontalLayout.Alignment.CENTER);

                var signalName = new Span(entry.getKey());
                signalName.addClassName("ar-signal-name");

                var signalValue = new Span("%.2f".formatted(entry.getValue()));
                signalValue.addClassName("ar-signal-value");

                signalRow.add(signalName, signalValue);
                auditContent.add(signalRow);
            }

            var details = new Details("Signal Audit", auditContent);
            details.setOpened(false);
            details.addClassName("ar-details-small");
            section.add(details);
        }

        return section;
    }

    private String trustScoreHealth(double score) {
        if (score >= 0.7) {
            return "good";
        }
        if (score >= 0.4) {
            return "warn";
        }
        return "bad";
    }

    private Span promotionZoneBadge(String zone) {
        var badge = new Span(zone);
        badge.addClassName("ar-badge");
        var dataZone = switch (zone) {
            case "AUTO_PROMOTE" -> "auto-promote";
            case "REVIEW" -> "review";
            default -> "archive";
        };
        badge.getElement().setAttribute("data-zone", dataZone);
        return badge;
    }

    private void styleRankBar(ProgressBar bar, int rank) {
        String color;
        if (rank >= 700) {
            color = "var(--lumo-error-color)";   // high rank = red (critical)
        } else if (rank >= 400) {
            color = "var(--lumo-warning-color)"; // medium rank = orange
        } else {
            color = "var(--lumo-primary-color)"; // low rank = blue
        }
        bar.getStyle().set("--vaadin-progress-bar-value-color", color);
    }

    private Span authorityBadge(String authority) {
        var badge = new Span(authority);
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-authority", authority.toLowerCase());
        return badge;
    }

    private void renderVerdicts(List<EvalVerdict> verdicts) {
        var contradicted = verdicts.stream()
                                   .filter(v -> v.verdict() == EvalVerdict.Verdict.CONTRADICTED)
                                   .count();
        verdictCountBadge.setText(String.valueOf(contradicted));
        verdictCountBadge.removeClassName("ar-verdict-count-badge--error");
        verdictCountBadge.removeClassName("ar-verdict-count-badge--success");
        if (contradicted > 0) {
            verdictCountBadge.addClassName("ar-verdict-count-badge--error");
        } else {
            verdictCountBadge.addClassName("ar-verdict-count-badge--success");
        }

        for (var verdict : verdicts) {
            verdictsContent.add(verdictCard(verdict));
        }
    }

    private Div verdictCard(EvalVerdict verdict) {
        var card = new Div();
        card.addClassName("ar-verdict-card");

        var verdictText = switch (verdict.verdict()) {
            case CONTRADICTED -> "CONTRADICTED";
            case CONFIRMED -> "CONFIRMED";
            default -> "NOT MENTIONED";
        };

        var dataVerdict = switch (verdict.verdict()) {
            case CONTRADICTED -> "contradicted";
            case CONFIRMED -> "confirmed";
            default -> "not-mentioned";
        };
        card.getElement().setAttribute("data-verdict", dataVerdict);

        var header = new HorizontalLayout();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.setSpacing(true);

        var factId = new Span(verdict.factId());
        factId.addClassName("ar-fact-id");

        var verdictBadge = new Span(verdictText);
        verdictBadge.addClassName("ar-badge");
        verdictBadge.getElement().setAttribute("data-verdict", dataVerdict);

        header.add(factId, verdictBadge);
        card.add(header);

        if (verdict.explanation() != null && !verdict.explanation().isBlank()) {
            var explanation = new Paragraph(verdict.explanation());
            explanation.addClassName("ar-verdict-explanation");
            card.add(explanation);
        }

        return card;
    }

    private HorizontalLayout metadataRow(String label, String value) {
        var row = new HorizontalLayout();
        row.setSpacing(true);
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);

        var labelSpan = new Span(label + ":");
        labelSpan.addClassName("ar-verdict-detail-label");

        var valueSpan = new Span(value);
        valueSpan.addClassName("ar-verdict-detail-value");

        row.add(labelSpan, valueSpan);
        return row;
    }

    private void showPlaceholder(VerticalLayout container, String message) {
        var placeholder = new Paragraph(message);
        placeholder.addClassName("ar-placeholder");
        container.add(placeholder);
    }

    private Span tabBadge(String text, String dataAttribute, String dataValue) {
        var badge = new Span(text);
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute(dataAttribute, dataValue);
        return badge;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
