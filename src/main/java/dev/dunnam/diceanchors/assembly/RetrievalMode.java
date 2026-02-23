package dev.dunnam.diceanchors.assembly;

/**
 * Controls how anchors are selected for prompt injection.
 * <ul>
 *   <li>{@code BULK} — all active anchors within budget (legacy behavior)</li>
 *   <li>{@code TOOL} — anchors retrieved on-demand via tool calls (empty baseline)</li>
 *   <li>{@code HYBRID} — heuristic/LLM-scored selection of top-k anchors</li>
 * </ul>
 */
public enum RetrievalMode { BULK, TOOL, HYBRID }
