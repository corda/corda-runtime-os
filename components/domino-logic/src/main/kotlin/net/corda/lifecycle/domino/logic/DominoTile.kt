package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus

/**
 * This interface can be implemented by classes the encapsulate more elaborate domino logic that can be reused easily.
 */
interface DominoTile: Lifecycle {
    /**
     * The coordinator name that will be used by this domino tile for lifecycle events.
     */
    val coordinatorName: LifecycleCoordinatorName

    /**
     * This tiles coordinator.
     */
    val coordinator: LifecycleCoordinator

    /**
     * Coordinators this tile is dependent upon.
     * This tile will wait for these children to have [LifecycleStatus.UP] before starting itself.
     * If any of them goes down or has an error, it will also go down.
     */
    val dependentChildren: Collection<LifecycleCoordinatorName>

    /**
     * Lifecycle components that are managed by this tile.
     * This tile is responsible for invoking [start] on these children when it is started.
     * It is also responsible for invoking [stop] when it is stopped.
     */
    val managedChildren: Collection<ManagedChild>

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun close() {
        coordinator.close()
    }

    override fun stop() {
        coordinator.stop()
    }

    fun toManagedChild(): ManagedChild {
        return ManagedChild(this.coordinator, this)
    }
}