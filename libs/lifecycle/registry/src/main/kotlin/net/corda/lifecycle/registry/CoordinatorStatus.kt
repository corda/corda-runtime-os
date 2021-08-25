package net.corda.lifecycle.registry

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus

/**
 * A description of the status of a single coordinator.
 *
 * This is returned by the registry when describing the current coordinator status.
 *
 * @param name The name of the coordinator
 * @param status The status of the coordinator
 * @param reason A description of what put this coordinator into this status.
 */
data class CoordinatorStatus(val name: LifecycleCoordinatorName, val status: LifecycleStatus, val reason: String)
