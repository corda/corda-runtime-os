package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus

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
    val state: LifecycleStatus

    /**
     * Coordinators this tile is dependent upon.
     * This tile will wait for these children to have [LifecycleStatus.UP] before starting itself.
     * If any of them goes down or has an error, it will also go down.
     */
    val dependentChildren: Collection<LifecycleCoordinatorName>

    /**
     * Domino tiles that are managed by this tile.
     * This tile is responsible for invoking [start] on these tiles when it is started.
     * It is also responsible for invoking [stop] when it is stopped.
     */
    val managedChildren: Collection<DominoTile>
}