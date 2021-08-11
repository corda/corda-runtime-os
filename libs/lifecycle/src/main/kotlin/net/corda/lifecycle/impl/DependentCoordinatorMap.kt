package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleState
import java.util.concurrent.ConcurrentHashMap

/**
 * Map of coordinators that are dependent on the local coordinator.
 *
 * Coordinators are added to the map in a thread safe manner and are ref counted. This allows for multiple registrations
 * of the same coordinator, which only become unregistered when all registrations are closed.
 */
internal class DependentCoordinatorMap {

    private val coordinatorMap : MutableMap<LifecycleCoordinator, Int> = ConcurrentHashMap()

    /**
     * Add a new coordinator to the map.
     *
     * For coordinators already registered for updates, the ref count is incremented instead.
     */
    fun addCoordinator(coordinator: LifecycleCoordinator) {
        coordinatorMap.merge(coordinator, 1, Int::plus)
    }

    /**
     * Remove a coordinator registration.
     *
     * Coordinators are only removed entirely if the ref count drops to zero.
     */
    fun removeCoordinator(coordinator: LifecycleCoordinator) {
        coordinatorMap.computeIfPresent(coordinator) { _, refCount ->
            val newCount = refCount - 1
            if (newCount == 0) {
                null
            } else {
                newCount
            }
        }
    }

    /**
     * Update all dependent coordinators of the new state of this coordinator.
     */
    fun updateStatus(localCoordinator: LifecycleCoordinator, newState: LifecycleState) {
        coordinatorMap.keys.forEach {
            it.postEvent(ActiveChangeInternal(localCoordinator, newState))
        }
    }
}