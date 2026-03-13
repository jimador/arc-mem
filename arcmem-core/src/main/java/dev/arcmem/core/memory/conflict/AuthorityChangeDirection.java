package dev.arcmem.core.memory.conflict;

/**
 * Direction of an authority transition, carried in
 * {@link dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged}.
 *
 * @see dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged
 */
public enum AuthorityChangeDirection {

    /**
     * Authority was increased (e.g., PROVISIONAL → UNRELIABLE via reinforcement).
     */
    PROMOTED,

    /**
     * Authority was decreased (e.g., RELIABLE → UNRELIABLE via conflict or decay).
     */
    DEMOTED
}
