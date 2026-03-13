package dev.arcmem.core.assembly.retrieval;

/**
 * Controls how units are selected for prompt injection.
 * <ul>
 *   <li>{@code BULK} — all active units within budget (legacy behavior)</li>
 *   <li>{@code TOOL} — units retrieved on-demand via tool calls (empty baseline)</li>
 *   <li>{@code HYBRID} — heuristic/LLM-scored selection of top-k units</li>
 * </ul>
 */
public enum RetrievalMode {BULK, TOOL, HYBRID}
