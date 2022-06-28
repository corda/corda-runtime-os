package net.corda.lifecycle.impl.registry

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorInternal

/**
 * Interface for functions to access the registry from the coordinators.
 *
 * Used by coordinators to update the registry of their current status, and by the factory when a new coordinator is
 * created. Used purely internally to the lifecycle library.
 */
interface LifecycleRegistryCoordinatorAccess {

    /**
     * Start tracking a new coordinator under a particular name.
     *
     * @param name The name of the coordinator to track.
     * @param coordinator The new coordinator.
     * @throws LifecycleRegistryException if there is already a coordinator registered under this name
     */
    fun registerCoordinator(name: LifecycleCoordinatorName, coordinator: LifecycleCoordinatorInternal)

    /**
     * Update the registry of current lifecycle status of a named coordinator.
     *
     * This triggers any monitoring components registered to be updated with the new status.
     *
     * @param name The name of the coordinator which has had a status change.
     * @param status The new coordinator status.
     * @param reason The reason this coordinator status changed.
     * @throws LifecycleRegistryException If there is no coordinator registered under the provided name
     */
    fun updateStatus(name: LifecycleCoordinatorName, status: LifecycleStatus, reason: String)

    /**
     * Retrieve a coordinator for a given name.
     *
     * @param name The name of the coordinator to retrieve
     * @return The coordinator for the given name
     * @throws LifecycleRegistryException if there is no coordinator registered under this name
     */
    fun getCoordinator(name: LifecycleCoordinatorName): LifecycleCoordinatorInternal

    /**
     * Stop tracking the coordinator with a given name.
     *
     * @param name The name of the coordinator to stop tracking.
     */
    fun removeCoordinator(name: LifecycleCoordinatorName)
}
