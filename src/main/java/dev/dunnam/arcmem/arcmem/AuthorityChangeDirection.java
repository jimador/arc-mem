package dev.dunnam.diceanchors.anchor;

/**
 * Direction of an authority transition, carried in
 * {@link dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent.AuthorityChanged}.
 *
 * @see dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent.AuthorityChanged
 */
public enum AuthorityChangeDirection {

    /** Authority was increased (e.g., PROVISIONAL → UNRELIABLE via reinforcement). */
    PROMOTED,

    /** Authority was decreased (e.g., RELIABLE → UNRELIABLE via conflict or decay). */
    DEMOTED
}
