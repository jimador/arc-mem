/**
 * ARC-Mem memory domain.
 *
 * <p>The memory domain is intentionally split into subpackages by responsibility:
 *
 * <ul>
 *   <li>{@code model}: memory unit model and authority/tier primitives</li>
 *   <li>{@code engine}: ARC-Mem orchestration and projection entry points</li>
 *   <li>{@code trust}: trust signals, trust scoring, and trust evaluation</li>
 *   <li>{@code conflict}: contradiction detection, indexing, and resolution</li>
 *   <li>{@code maintenance}: decay, pressure, and sweep strategies</li>
 *   <li>{@code canon}: canonization, invariants, and compliance policy</li>
 *   <li>{@code budget}: working-memory budget strategies</li>
 *   <li>{@code mutation}: mutation/reinforcement contracts</li>
 *   <li>{@code attention}: attention tracking support</li>
 *   <li>{@code event}: lifecycle events and listeners</li>
 * </ul>
 */
package dev.arcmem.core.memory;
