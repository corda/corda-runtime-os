package net.corda.lifecycle.registry

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus

/**
 * Obtain information about the current running status of coordinators in the system.
 *
 * This is used to monitor the status of components in the system using the lifecycle library. On creating a new
 * coordinator, it is added to the registry under the provided name. Data about the current status of the coordinators
 * in the system can then be queried for. Coordinators are added to the registry automatically on creation.
 *
 * Adding multiple coordinators under the same name is supported to cope with component reuse. An optional scope
 * parameter can be provided at coordinator creation time to declare what purpose this instance of the component is for.
 * If this is omitted, the registry will assign an integer suffix for every coordinator supplied after the first.
 *
 * This interface is concerned with providing the read side of component data and should be used only by monitoring
 * components.
 */
interface LifecycleRegistry {

    /**
     * Obtain the current status of all objects.
     *
     * @return A map of coordinator names to their current statuses.
     */
    fun componentStatus(): Map<LifecycleCoordinatorName, CoordinatorStatus>

    /**
     * Returns all [LifecycleCoordinatorName] in the given statuses
     */
    fun componentWithStatus(statuses: Collection<LifecycleStatus>) =
        componentStatus().values.filter { coordinatorStatus ->
            statuses.contains(coordinatorStatus.status)
        }.map {
            it.name
        }
}