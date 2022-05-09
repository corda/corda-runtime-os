package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * This interface can be implemented by classes the encapsulate more elaborate domino logic that can be reused easily.
 */
interface DominoTile: Lifecycle {
    /**
     * the coordinator name that will be used by this domino tile for lifecycle events.
     */
    val coordinatorName: LifecycleCoordinatorName

    /**
     * The current state of the domino tile.
     */
    val state: DominoTileState

    /**
     * Domino tiles this tile is dependent upon.
     * This tile will wait for these children to start before starting itself.
     * If any of them goes down or has an error, it will also go down.
     */
    val dependentChildren: Collection<DominoTile>

    /**
     * Domino tiles that are managed by this tile.
     * This tile is responsible for invoking [start] on these tiles when it is started.
     * It is also responsible for invoking [stop] when it is stopped.
     */
    val managedChildren: Collection<DominoTile>
}

enum class DominoTileState {
    Created,
    Started,
    StoppedDueToError,
    StoppedDueToBadConfig,
    StoppedDueToChildStopped,
    StoppedByParent
}

/**
 * Every time the state of a domino tile changes, it is responsible of sending a [CustomEvent] with the new state to any tiles that are
 * registered to follow any changes.
 */
data class StatusChangeEvent(val newState: DominoTileState)